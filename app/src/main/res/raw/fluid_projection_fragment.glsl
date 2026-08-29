#version 300 es
precision highp float;

uniform sampler2D u_velocity;
uniform sampler2D u_pressure;
uniform vec2 u_texel;
in vec2 v_uv;
layout(location = 0) out vec4 o_color;

float pressure_at(vec2 uv) {
    return texture(u_pressure, clamp(uv, u_texel * 0.5, vec2(1.0) - u_texel * 0.5)).r;
}

void main() {
    float pressure_left = pressure_at(v_uv - vec2(u_texel.x, 0.0));
    float pressure_right = pressure_at(v_uv + vec2(u_texel.x, 0.0));
    float pressure_bottom = pressure_at(v_uv - vec2(0.0, u_texel.y));
    float pressure_top = pressure_at(v_uv + vec2(0.0, u_texel.y));
    vec2 pressure_gradient = 0.5 * vec2(
        pressure_right - pressure_left,
        pressure_top - pressure_bottom
    );
    vec2 projected_velocity = texture(u_velocity, v_uv).rg - pressure_gradient;
    if (v_uv.x <= u_texel.x || v_uv.x >= 1.0 - u_texel.x) projected_velocity.x = 0.0;
    if (v_uv.y <= u_texel.y || v_uv.y >= 1.0 - u_texel.y) projected_velocity.y = 0.0;
    o_color = vec4(projected_velocity, 0.0, 1.0);
}
