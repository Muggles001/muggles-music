#version 300 es
precision highp float;

layout(location = 0) in vec4 a_position_velocity;
layout(location = 1) in vec4 a_material;
layout(location = 2) in vec4 a_identity;

uniform vec2 u_deposit_resolution;

out vec4 v_splat_data;
out vec2 v_splat_velocity;

void main() {
    vec2 position = a_position_velocity.xy;
    vec2 pixel_velocity = a_position_velocity.zw * u_deposit_resolution;
    float mass = clamp(a_material.x, 0.0, 1.0);
    float flow_response = clamp(a_material.y, 0.0, 1.0);
    float normalized_age = clamp(a_material.z / max(a_material.w, 1.0), 0.0, 1.0);
    v_splat_data = vec4(
        mass,
        mass * flow_response,
        mass * normalized_age,
        mass * mix(0.35, 0.85, normalized_age)
    );
    v_splat_velocity = pixel_velocity;
    float directional_size = 3.0 + min(length(pixel_velocity) * 0.06, 3.0);
    gl_PointSize = directional_size;
    gl_Position = vec4(position * 2.0 - 1.0, 0.0, 1.0);
}
