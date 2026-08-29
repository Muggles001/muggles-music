#version 300 es
precision highp float;

uniform sampler2D u_pressure;
uniform sampler2D u_divergence;
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
    float divergence = texture(u_divergence, v_uv).r;
    float pressure = (pressure_left + pressure_right + pressure_bottom + pressure_top - divergence) * 0.25;
    o_color = vec4(pressure, 0.0, 0.0, 1.0);
}
