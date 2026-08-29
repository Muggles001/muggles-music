#version 300 es
precision highp float;

uniform sampler2D u_source;
uniform vec2 u_source_texel;
uniform vec2 u_source_scale;
in vec2 v_uv;
layout(location = 0) out vec4 o_color;

void main() {
    const float tap_positions[4] = float[4](-0.375, -0.125, 0.125, 0.375);
    vec4 sample_sum = vec4(0.0);
    vec4 sample_peak = vec4(0.0);
    for (int y = 0; y < 4; ++y) {
        for (int x = 0; x < 4; ++x) {
            vec2 offset = vec2(tap_positions[x], tap_positions[y])
                * u_source_scale * u_source_texel;
            vec4 sample_value = texture(u_source, v_uv + offset);
            sample_sum += sample_value;
            sample_peak = max(sample_peak, sample_value);
        }
    }
    o_color = sample_peak * 0.55 + sample_sum * (0.45 / 16.0);
}
