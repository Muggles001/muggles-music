#version 300 es
precision highp float;

uniform sampler2D u_velocity;
uniform vec2 u_texel;
in vec2 v_uv;
layout(location = 0) out vec4 o_color;

vec2 bounded_velocity(vec2 uv) {
    vec2 sample_uv = clamp(uv, u_texel * 0.5, vec2(1.0) - u_texel * 0.5);
    vec2 velocity = texture(u_velocity, sample_uv).rg;
    if (uv.x <= 0.0 || uv.x >= 1.0) velocity.x = 0.0;
    if (uv.y <= 0.0 || uv.y >= 1.0) velocity.y = 0.0;
    return velocity;
}

void main() {
    vec2 velocity_left = bounded_velocity(v_uv - vec2(u_texel.x, 0.0));
    vec2 velocity_right = bounded_velocity(v_uv + vec2(u_texel.x, 0.0));
    vec2 velocity_bottom = bounded_velocity(v_uv - vec2(0.0, u_texel.y));
    vec2 velocity_top = bounded_velocity(v_uv + vec2(0.0, u_texel.y));
    float divergence = 0.5 * (
        velocity_right.x - velocity_left.x + velocity_top.y - velocity_bottom.y
    );
    o_color = vec4(divergence, 0.0, 0.0, 1.0);
}
