#version 300 es
precision highp float;

uniform sampler2D u_transient;
uniform sampler2D u_history;
uniform sampler2D u_velocity;
uniform vec2 u_velocity_texel;
uniform float u_delta_time;
in vec2 v_uv;
layout(location = 0) out vec4 o_color;

vec2 velocity_at(vec2 uv) {
    vec2 bounded_uv = clamp(
        uv,
        u_velocity_texel * 0.5,
        vec2(1.0) - u_velocity_texel * 0.5
    );
    vec2 velocity = texture(u_velocity, bounded_uv).rg;
    if (uv.x <= 0.0 || uv.x >= 1.0) velocity.x = 0.0;
    if (uv.y <= 0.0 || uv.y >= 1.0) velocity.y = 0.0;
    return velocity;
}

float compute_shear(vec2 uv) {
    vec2 left = velocity_at(uv - vec2(u_velocity_texel.x, 0.0));
    vec2 right = velocity_at(uv + vec2(u_velocity_texel.x, 0.0));
    vec2 bottom = velocity_at(uv - vec2(0.0, u_velocity_texel.y));
    vec2 top = velocity_at(uv + vec2(0.0, u_velocity_texel.y));
    vec2 derivative_x = (right - left) / max(2.0 * u_velocity_texel.x, 0.000001);
    vec2 derivative_y = (top - bottom) / max(2.0 * u_velocity_texel.y, 0.000001);
    return length(vec2(derivative_x.y + derivative_y.x, derivative_x.x - derivative_y.y));
}

void main() {
    float delta_time = clamp(u_delta_time, 0.0, 0.0333333);
    vec4 transient_occupancy = clamp(texture(u_transient, v_uv) * 13.3333, 0.0, 1.0);
    vec4 history = clamp(texture(u_history, v_uv), 0.0, 1.0);
    float shear = compute_shear(v_uv);
    float shear_erosion = smoothstep(0.16, 0.55, shear);

    vec4 target = transient_occupancy;
    target.b = target.g > 0.0001 ? clamp(target.b / target.g, 0.0, 1.0) : 0.0;
    target.a = target.g > 0.0001 ? clamp(target.a / target.g, 0.0, 1.0) : 0.0;
    vec4 history_relaxation = vec4(
        1.0 - exp(-8.0 * delta_time),
        1.0 - exp(-3.5 * delta_time),
        1.0 - exp(-1.8 * delta_time),
        1.0 - exp(-1.2 * delta_time)
    );
    vec4 resolved = mix(history, target, history_relaxation);
    resolved.r *= 1.0 / (1.0 + delta_time * (0.22 + shear_erosion * 1.8));
    resolved.g *= 1.0 / (1.0 + delta_time * (0.035 + shear_erosion * 2.4));
    resolved.b *= 1.0 / (1.0 + delta_time * (0.020 + shear_erosion * 1.4));
    resolved.a *= 1.0 / (1.0 + delta_time * (0.014 + shear_erosion * 1.1));
    o_color = clamp(resolved, 0.0, 1.0);
}
