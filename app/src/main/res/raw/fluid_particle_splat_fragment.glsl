#version 300 es
precision highp float;

in vec4 v_splat_data;
in vec2 v_splat_velocity;
layout(location = 0) out vec4 o_color;

void main() {
    vec2 local = gl_PointCoord * 2.0 - 1.0;
    float speed = length(v_splat_velocity);
    vec2 direction = speed > 0.001 ? v_splat_velocity / speed : vec2(1.0, 0.0);
    vec2 tangent = vec2(-direction.y, direction.x);
    vec2 aligned = vec2(dot(local, direction), dot(local, tangent));
    float mobile_distance = length(vec2(aligned.x * 0.72, aligned.y * 1.25));
    float coverage = 1.0 - smoothstep(0.62, 1.0, mobile_distance);
    o_color = clamp(v_splat_data * coverage * 0.075, 0.0, 1.0);
}
