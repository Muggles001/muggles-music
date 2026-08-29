#version 300 es
precision highp float;

layout(location = 0) in vec4 a_position_velocity;
layout(location = 1) in vec4 a_material;
layout(location = 2) in vec4 a_identity;

uniform vec2 u_surface_resolution;
uniform sampler2D u_deposit;
uniform sampler2D u_carrier;
uniform vec2 u_carrier_texel;
uniform float u_time;

out vec4 v_emissive;

float stable_hash(float value) {
    vec2 point = fract(vec2(value, value * 1.6180339) * vec2(0.1031, 0.1030));
    point += dot(point, point.yx + 19.19);
    return fract((point.x + point.y) * point.x);
}

void main() {
    vec2 position = a_position_velocity.xy;
    float mass = clamp(a_material.x, 0.0, 1.0);
    float particle_age = clamp(a_material.z / max(a_material.w, 1.0), 0.0, 1.0);
    float stable_seed = a_identity.x;
    vec4 deposit = texture(u_deposit, position);
    float center = texture(u_carrier, position).r;
    float left = texture(u_carrier, position - vec2(u_carrier_texel.x, 0.0)).r;
    float right = texture(u_carrier, position + vec2(u_carrier_texel.x, 0.0)).r;
    float bottom = texture(u_carrier, position - vec2(0.0, u_carrier_texel.y)).r;
    float top = texture(u_carrier, position + vec2(0.0, u_carrier_texel.y)).r;
    float carrier_curvature = abs(left + right + bottom + top - center * 4.0);
    float stable_selection = step(0.9988, stable_hash(stable_seed * 91.7));
    float right_upper_region = smoothstep(0.38, 0.72, position.x)
        * smoothstep(0.42, 0.76, position.y);
    float material_attachment = smoothstep(0.005, 0.055, carrier_curvature)
        * smoothstep(0.02, 0.42, deposit.r + center * 0.24);
    float frequency = mix(0.8, 2.0, stable_hash(stable_seed * 47.3));
    float continuous_breath = 0.24 + 0.76 * pow(
        sin(u_time * 6.2831853 * frequency + stable_seed * 6.2831853) * 0.5 + 0.5,
        2.2
    );
    float eligibility = mass * stable_selection * right_upper_region
        * material_attachment * continuous_breath;
    v_emissive = vec4(eligibility, stable_seed, particle_age, deposit.a);
    float point_pixels = mix(1.2, 3.0, stable_hash(stable_seed * 17.9));
    gl_PointSize = max(1.0, point_pixels * u_surface_resolution.y / 1080.0);
    gl_Position = vec4(position * 2.0 - 1.0, 0.0, 1.0);
}
