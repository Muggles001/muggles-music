#version 300 es
precision highp float;

uniform sampler2D u_carrier;
uniform sampler2D u_deposit;
uniform vec2 u_surface_resolution;
uniform vec2 u_carrier_texel;
uniform vec2 u_deposit_texel;
in vec2 v_uv;
layout(location = 0) out vec4 o_color;

vec3 srgb_to_linear(vec3 srgb_color) {
    vec3 low = srgb_color / 12.92;
    vec3 high = pow((srgb_color + 0.055) / 1.055, vec3(2.4));
    bvec3 cutoff = lessThanEqual(srgb_color, vec3(0.04045));
    return mix(high, low, cutoff);
}

float carrier_height(vec2 uv) {
    vec4 carrier = texture(u_carrier, clamp(uv, vec2(0.0), vec2(1.0)));
    return clamp(carrier.r * 0.72 - carrier.g * 0.42 + carrier.b * 0.20, 0.0, 1.0);
}

float deposit_height(vec2 uv) {
    vec4 deposit = texture(u_deposit, clamp(uv, vec2(0.0), vec2(1.0)));
    return clamp(deposit.r * 0.34 + deposit.g * 0.46, 0.0, 1.0);
}

vec2 carrier_gradient(vec2 uv, vec2 texel) {
    return vec2(
        carrier_height(uv + vec2(texel.x, 0.0)) - carrier_height(uv - vec2(texel.x, 0.0)),
        carrier_height(uv + vec2(0.0, texel.y)) - carrier_height(uv - vec2(0.0, texel.y))
    ) * 0.5;
}

vec2 deposit_gradient(vec2 uv, vec2 texel) {
    return vec2(
        deposit_height(uv + vec2(texel.x, 0.0)) - deposit_height(uv - vec2(texel.x, 0.0)),
        deposit_height(uv + vec2(0.0, texel.y)) - deposit_height(uv - vec2(0.0, texel.y))
    ) * 0.5;
}

vec2 carrier_form_gradient(vec2 uv, vec2 texel) {
    float top_left = carrier_height(uv + vec2(-texel.x, texel.y));
    float top = carrier_height(uv + vec2(0.0, texel.y));
    float top_right = carrier_height(uv + vec2(texel.x, texel.y));
    float left = carrier_height(uv + vec2(-texel.x, 0.0));
    float right = carrier_height(uv + vec2(texel.x, 0.0));
    float bottom_left = carrier_height(uv + vec2(-texel.x, -texel.y));
    float bottom = carrier_height(uv + vec2(0.0, -texel.y));
    float bottom_right = carrier_height(uv + vec2(texel.x, -texel.y));
    return vec2(
        top_right + 2.0 * right + bottom_right - top_left - 2.0 * left - bottom_left,
        top_left + 2.0 * top + top_right - bottom_left - 2.0 * bottom - bottom_right
    ) * 0.125;
}

vec2 deposit_form_gradient(vec2 uv, vec2 texel) {
    float top_left = deposit_height(uv + vec2(-texel.x, texel.y));
    float top = deposit_height(uv + vec2(0.0, texel.y));
    float top_right = deposit_height(uv + vec2(texel.x, texel.y));
    float left = deposit_height(uv + vec2(-texel.x, 0.0));
    float right = deposit_height(uv + vec2(texel.x, 0.0));
    float bottom_left = deposit_height(uv + vec2(-texel.x, -texel.y));
    float bottom = deposit_height(uv + vec2(0.0, -texel.y));
    float bottom_right = deposit_height(uv + vec2(texel.x, -texel.y));
    return vec2(
        top_right + 2.0 * right + bottom_right - top_left - 2.0 * left - bottom_left,
        top_left + 2.0 * top + top_right - bottom_left - 2.0 * bottom - bottom_right
    ) * 0.125;
}

void main() {
    vec4 carrier = texture(u_carrier, v_uv);
    vec4 deposit = texture(u_deposit, v_uv);
    float height = carrier_height(v_uv) + deposit_height(v_uv) * 0.20;
    vec2 carrier_detail = carrier_gradient(v_uv, u_carrier_texel);
    vec2 carrier_form = carrier_form_gradient(v_uv, u_carrier_texel * 8.0);
    vec2 deposit_detail = deposit_gradient(v_uv, u_deposit_texel);
    vec2 deposit_form = deposit_form_gradient(v_uv, u_deposit_texel * 3.0);
    vec2 gradient = carrier_form * 0.84 + carrier_detail * 0.05
        + deposit_form * 0.09 + deposit_detail * 0.01;
    float aspect = u_surface_resolution.x / max(u_surface_resolution.y, 1.0);
    vec3 normal = normalize(vec3(-gradient.x * aspect * 4.3, -gradient.y * 4.3, 1.0));
    vec3 light_direction = normalize(vec3(-0.36, 0.42, 0.83));
    float diffuse = 0.76 + max(dot(normal, light_direction), 0.0) * 0.24;
    float broad_specular = pow(max(dot(reflect(-light_direction, normal), vec3(0.0, 0.0, 1.0)), 0.0), 20.0);
    float narrow_crest = smoothstep(0.78, 0.95, height)
        * smoothstep(0.34, 0.74, broad_specular);
    float basin = clamp(carrier.g * 0.82 + deposit.g * 0.14, 0.0, 1.0);
    float shallow_occlusion = clamp(1.0 - basin * 0.16 - length(gradient) * 0.08, 0.84, 1.0);

    vec3 shade_mint = srgb_to_linear(vec3(0.282, 0.663, 0.506));
    vec3 body_mint = srgb_to_linear(vec3(0.392, 0.765, 0.604));
    vec3 clear_mint = srgb_to_linear(vec3(0.545, 0.843, 0.722));
    vec3 pale_mint = srgb_to_linear(vec3(0.714, 0.914, 0.831));
    vec3 cream_crest = srgb_to_linear(vec3(0.847, 0.957, 0.914));
    float body_weight = smoothstep(0.12, 0.82, carrier.r);
    float material_tracer = smoothstep(0.30, 0.70, carrier.b);
    vec3 density_color = mix(body_mint, clear_mint, body_weight);
    vec3 tracer_color = mix(shade_mint, pale_mint, material_tracer);
    vec3 color = mix(density_color, tracer_color, 0.36);
    color = mix(color, shade_mint, smoothstep(0.58, 0.90, basin) * 0.18);
    float tracer_shadow = smoothstep(0.48, 0.86, 1.0 - material_tracer)
        * (1.0 - smoothstep(0.82, 0.98, body_weight));
    color = mix(color, shade_mint, tracer_shadow * 0.08);
    float transmission_mask = smoothstep(0.20, 0.74, carrier.r)
        * (1.0 - smoothstep(0.38, 0.76, basin));
    vec3 transmitted_mint = mix(clear_mint, pale_mint, 0.28);
    color = mix(color, transmitted_mint, transmission_mask * 0.08);
    color = mix(color, pale_mint,
        smoothstep(0.78, 0.98, carrier.r) * (1.0 - basin) * 0.09);
    color *= diffuse * shallow_occlusion;
    color += pale_mint * broad_specular * 0.030;
    color += cream_crest * narrow_crest * 0.050;
    o_color = vec4(clamp(color, 0.0, 1.0), 1.0);
}
