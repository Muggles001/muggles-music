#version 300 es
precision highp float;

uniform sampler2D u_source;
uniform vec2 u_source_texel;
uniform vec2 u_direction;
in vec2 v_uv;
layout(location = 0) out vec4 o_color;

void main() {
    vec2 step_offset = u_source_texel * u_direction;
    vec4 color = texture(u_source, v_uv) * 0.227027;
    color += texture(u_source, v_uv + step_offset * 1.384615) * 0.316216;
    color += texture(u_source, v_uv - step_offset * 1.384615) * 0.316216;
    color += texture(u_source, v_uv + step_offset * 3.230769) * 0.070270;
    color += texture(u_source, v_uv - step_offset * 3.230769) * 0.070270;
    o_color = color;
}
