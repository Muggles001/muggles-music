#version 300 es
precision highp float;

uniform sampler2D u_scene;
uniform sampler2D u_emissive;
uniform sampler2D u_bloom_near;
uniform sampler2D u_bloom_far;
in vec2 v_uv;
layout(location = 0) out vec4 o_color;

vec3 linear_to_srgb(vec3 linear_color) {
    vec3 low = linear_color * 12.92;
    vec3 high = 1.055 * pow(max(linear_color, vec3(0.0)), vec3(1.0 / 2.4)) - 0.055;
    bvec3 cutoff = lessThanEqual(linear_color, vec3(0.0031308));
    return mix(high, low, cutoff);
}

vec3 compress_hdr_glow(vec3 glow) {
    float peak = max(max(glow.r, glow.g), glow.b);
    return glow / (1.0 + peak * 0.55);
}

void main() {
    vec3 scene = texture(u_scene, v_uv).rgb;
    vec3 emissive = texture(u_emissive, v_uv).rgb;
    vec3 near_light = texture(u_bloom_near, v_uv).rgb * 0.24;
    vec3 far_light = texture(u_bloom_far, v_uv).rgb * 0.12;
    vec3 hdr_glow = emissive + near_light + far_light;
    vec3 linear_color = scene + compress_hdr_glow(hdr_glow);
    o_color = vec4(clamp(linear_to_srgb(linear_color), 0.0, 1.0), 1.0);
}
