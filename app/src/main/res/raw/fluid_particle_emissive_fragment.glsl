#version 300 es
precision highp float;

in vec4 v_emissive;
layout(location = 0) out vec4 o_color;

float stable_hash(float value) {
    vec2 point = fract(vec2(value, value * 1.6180339) * vec2(0.1031, 0.1030));
    point += dot(point, point.yx + 19.19);
    return fract((point.x + point.y) * point.x);
}

void main() {
    vec2 local = gl_PointCoord * 2.0 - 1.0;
    float radius = length(local);
    float core = 1.0 - smoothstep(0.28, 0.92, radius);
    float horizontal = (1.0 - smoothstep(0.12, 0.34, abs(local.y)))
        * (1.0 - smoothstep(0.34, 1.0, abs(local.x)));
    float vertical = (1.0 - smoothstep(0.12, 0.34, abs(local.x)))
        * (1.0 - smoothstep(0.34, 1.0, abs(local.y)));
    float shape = clamp(core + max(horizontal, vertical) * 0.34, 0.0, 1.0);
    vec3 mint_white = vec3(0.68, 1.0, 0.90);
    vec3 cool_white = vec3(0.90, 0.98, 1.0);
    float color_choice = step(0.58, stable_hash(v_emissive.y * 53.1));
    vec3 sparkle_color = mix(mint_white, cool_white, color_choice);
    float intensity = v_emissive.x * mix(0.82, 1.0, v_emissive.z) * mix(0.90, 1.0, v_emissive.w);
    o_color = vec4(sparkle_color * shape * intensity, shape * intensity);
}
