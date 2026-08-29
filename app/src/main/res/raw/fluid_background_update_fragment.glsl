#version 300 es
precision highp float;

uniform sampler2D u_velocity;
uniform sampler2D u_carrier;
uniform vec2 u_texel;
uniform float u_delta_time;
uniform vec4 u_motion;
in vec2 v_uv;
layout(location = 0) out vec4 o_color;

float hash21(vec2 point) {
    point = mod(point, vec2(127.0, 113.0));
    point = fract(point * vec2(0.1031, 0.1030));
    point += dot(point, point.yx + 19.19);
    return fract((point.x + point.y) * point.x);
}

float value_noise(vec2 point) {
    vec2 cell = floor(point);
    vec2 local = fract(point);
    local = local * local * (3.0 - 2.0 * local);
    float a = hash21(cell);
    float b = hash21(cell + vec2(1.0, 0.0));
    float c = hash21(cell + vec2(0.0, 1.0));
    float d = hash21(cell + vec2(1.0, 1.0));
    return mix(mix(a, b, local.x), mix(c, d, local.x), local.y);
}

float stream_noise(vec2 point) {
    float broad = value_noise(point);
    point = mat2(1.62, 1.18, -1.18, 1.62) * point + 5.7;
    return broad * 0.68 + value_noise(point) * 0.32;
}

vec2 clamp_uv(vec2 uv) {
    return clamp(uv, u_texel * 0.5, vec2(1.0) - u_texel * 0.5);
}

vec2 sample_velocity(vec2 uv) {
    vec2 velocity = texture(u_velocity, clamp_uv(uv)).rg;
    if (uv.x <= u_texel.x || uv.x >= 1.0 - u_texel.x) velocity.x = 0.0;
    if (uv.y <= u_texel.y || uv.y >= 1.0 - u_texel.y) velocity.y = 0.0;
    return velocity;
}

vec2 rotational_flow(vec2 point, vec2 center, float strength, float radius) {
    vec2 delta = point - center;
    float radius_squared = radius * radius;
    float falloff = radius_squared / (dot(delta, delta) + radius_squared);
    return vec2(-delta.y, delta.x) * strength * falloff;
}

vec2 curl_velocity(vec2 point) {
    float epsilon = 0.025;
    vec2 drift = vec2(
        u_motion.x * 0.18 + u_motion.z * 0.07,
        u_motion.y * 0.16 + u_motion.w * 0.06
    );
    vec2 domain = point * 1.75 + drift;
    float positive_x = stream_noise(domain + vec2(epsilon, 0.0));
    float negative_x = stream_noise(domain - vec2(epsilon, 0.0));
    float positive_y = stream_noise(domain + vec2(0.0, epsilon));
    float negative_y = stream_noise(domain - vec2(0.0, epsilon));
    vec2 curl = vec2(positive_y - negative_y, negative_x - positive_x) / (2.0 * epsilon);
    return curl / (0.35 + length(curl));
}

vec2 broad_force(vec2 point) {
    vec2 offscreen_center = vec2(1.18, -0.06)
        + vec2(u_motion.z, u_motion.w) * vec2(0.08, 0.05);
    vec2 upper_right_center = vec2(0.46, 0.30)
        + vec2(-u_motion.y, u_motion.x) * vec2(0.04, 0.035);
    vec2 offscreen_return = rotational_flow(point, offscreen_center, -0.165, 0.64);
    vec2 upper_right_curl = rotational_flow(point, upper_right_center, -0.095, 0.19);
    vec2 force = offscreen_return + upper_right_curl;
    force += vec2(0.006, 0.002) * (0.72 + point.y * 0.24);
    force += curl_velocity(point) * 0.026;
    float magnitude = length(force);
    return force * min(1.0, 0.095 / max(magnitude, 0.0001));
}

vec2 physical_to_uv(vec2 velocity, float aspect) {
    return vec2(velocity.x / aspect, velocity.y);
}

void main() {
    float delta_time = clamp(u_delta_time, 0.0, 0.0333333);
    float aspect = u_texel.y / max(u_texel.x, 0.000001);
    vec2 point = vec2((v_uv.x - 0.5) * aspect, v_uv.y - 0.5);
    vec2 initial_velocity = sample_velocity(v_uv);
    vec2 force_uv = physical_to_uv(broad_force(point), aspect);
    vec2 predicted_velocity = initial_velocity + force_uv * delta_time;
    vec2 midpoint_uv = clamp_uv(v_uv - predicted_velocity * delta_time * 0.5);
    vec2 midpoint_velocity = sample_velocity(midpoint_uv);
    vec2 backtrace_uv = clamp_uv(v_uv - midpoint_velocity * delta_time);
    vec2 advected_velocity = sample_velocity(backtrace_uv);
    float concentration = texture(u_carrier, backtrace_uv).g;
    float concentration_damping = 1.0 / (1.0 + delta_time * (0.08 + concentration * 0.28));
    vec2 updated_velocity = (advected_velocity + force_uv * delta_time)
        * concentration_damping;
    float speed = length(updated_velocity);
    updated_velocity *= min(1.0, 0.18 / max(speed, 0.0001));
    if (v_uv.x <= u_texel.x || v_uv.x >= 1.0 - u_texel.x) updated_velocity.x = 0.0;
    if (v_uv.y <= u_texel.y || v_uv.y >= 1.0 - u_texel.y) updated_velocity.y = 0.0;
    o_color = vec4(updated_velocity, 0.0, 1.0);
}
