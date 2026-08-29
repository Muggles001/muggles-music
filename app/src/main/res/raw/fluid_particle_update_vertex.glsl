#version 300 es
precision highp float;

layout(location = 0) in vec4 a_position_velocity;
layout(location = 1) in vec4 a_material;
layout(location = 2) in vec4 a_identity;

uniform sampler2D u_velocity;
uniform vec2 u_velocity_texel;
uniform float u_delta_time;
uniform vec4 u_motion;

out vec4 v_position_velocity;
out vec4 v_material;
out vec4 v_identity;

const vec2 AMBIENT_TRANSPORT_UV = vec2(0.055, 0.010);

float stable_hash(float value) {
    vec2 point = fract(vec2(value, value * 1.6180339) * vec2(0.1031, 0.1030));
    point += dot(point, point.yx + 19.19);
    return fract((point.x + point.y) * point.x);
}

vec2 velocity_at(vec2 uv) {
    vec2 bounded_uv = clamp(
        uv,
        u_velocity_texel * 0.5,
        vec2(1.0) - u_velocity_texel * 0.5
    );
    vec2 velocity = texture(u_velocity, bounded_uv).rg;
    if (uv.x <= u_velocity_texel.x || uv.x >= 1.0 - u_velocity_texel.x) velocity.x = 0.0;
    if (uv.y <= u_velocity_texel.y || uv.y >= 1.0 - u_velocity_texel.y) velocity.y = 0.0;
    return velocity;
}

vec2 respawn_position(float stable_seed, float respawn_cycle) {
    float jitter_x = stable_hash(stable_seed * 29.3 + respawn_cycle * 7.7) - 0.5;
    float jitter_y = stable_hash(stable_seed * 47.9 + respawn_cycle * 11.3) - 0.5;
    vec2 edge_stream = vec2(0.025 + jitter_x * 0.018, 0.47 + jitter_y * 0.58);
    edge_stream.y += u_motion.y * 0.018;
    return clamp(edge_stream, vec2(0.01), vec2(0.99));
}

void main() {
    float delta_time = clamp(u_delta_time, 0.0, 0.0333333);
    vec2 position = a_position_velocity.xy;
    vec2 particle_velocity = a_position_velocity.zw;
    float mass = clamp(a_material.x, 0.05, 1.0);
    float flow_response = clamp(a_material.y, 0.25, 1.0);
    float state_age = max(0.0, a_material.z);
    float lifetime = max(1.0, a_material.w);
    float stable_seed = clamp(a_identity.x, 0.0001, 0.9999);
    float respawn_cycle = max(0.0, a_identity.y);
    float flow_phase = max(0.0, a_identity.z);

    vec2 margin = u_velocity_texel * 2.0;
    bool outside = any(lessThan(position, margin)) || any(greaterThan(position, vec2(1.0) - margin));
    if (outside || state_age >= lifetime) {
        respawn_cycle += 1.0;
        position = respawn_position(stable_seed, respawn_cycle);
        particle_velocity = AMBIENT_TRANSPORT_UV + velocity_at(position) * 0.35;
        flow_response = mix(0.52, 0.96, stable_hash(stable_seed * 31.7 + respawn_cycle));
        state_age = 0.0;
        flow_phase = stable_hash(stable_seed * 53.1 + respawn_cycle);
        lifetime = mix(9.0, 20.0, stable_hash(stable_seed * 71.9 + respawn_cycle));
    } else {
        vec2 carrier_velocity = velocity_at(position) + AMBIENT_TRANSPORT_UV;
        float response_rate = mix(3.8, 8.5, flow_response) * mix(0.82, 1.0, mass);
        float drag = 1.0 - exp(-delta_time * response_rate);
        particle_velocity = mix(particle_velocity, carrier_velocity, drag);
        vec2 tangent = normalize(vec2(-carrier_velocity.y, carrier_velocity.x) + vec2(0.0001));
        float lateral_sign = stable_hash(stable_seed * 83.7) * 2.0 - 1.0;
        particle_velocity += tangent * lateral_sign * 0.0018 * delta_time;
        vec2 midpoint = position + particle_velocity * delta_time * 0.5;
        vec2 midpoint_carrier = velocity_at(midpoint);
        position += mix(particle_velocity, midpoint_carrier, drag) * delta_time;
        flow_phase = fract(flow_phase + delta_time * mix(0.08, 0.20, flow_response));
        state_age += delta_time;
    }

    v_position_velocity = vec4(position, particle_velocity);
    v_material = vec4(mass, flow_response, state_age, lifetime);
    v_identity = vec4(stable_seed, respawn_cycle, flow_phase, 0.0);
    gl_Position = vec4(position * 2.0 - 1.0, 0.0, 1.0);
}
