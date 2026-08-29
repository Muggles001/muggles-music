#version 300 es
precision highp float;

uniform sampler2D u_carrier;
uniform sampler2D u_velocity;
uniform vec2 u_texel;
uniform float u_delta_time;
uniform vec4 u_motion;
uniform float u_reset;
in vec2 v_uv;
layout(location = 0) out vec4 o_color;

const vec2 AMBIENT_TRANSPORT_UV = vec2(0.055, 0.010);

float hash21(vec2 point) {
    point = mod(point, vec2(127.0, 113.0));
    point = fract(point * vec2(0.1031, 0.1030));
    point += dot(point, point.yx + 19.19);
    return fract((point.x + point.y) * point.x);
}

float value_noise(vec2 point) {
    vec2 cell = floor(point);
    vec2 local = fract(point);
    local = local * local * (3.0 - 2.0 * local);
    float a = hash21(cell);
    float b = hash21(cell + vec2(1.0, 0.0));
    float c = hash21(cell + vec2(0.0, 1.0));
    float d = hash21(cell + vec2(1.0, 1.0));
    return mix(mix(a, b, local.x), mix(c, d, local.x), local.y);
}

vec2 clamp_uv(vec2 uv) {
    return clamp(uv, u_texel * 0.5, vec2(1.0) - u_texel * 0.5);
}

vec2 velocity_at(vec2 uv) {
    return texture(u_velocity, clamp_uv(uv)).rg;
}

float soft_band(float coordinate, float center, float half_width) {
    return 1.0 - smoothstep(half_width * 0.45, half_width, abs(coordinate - center));
}

float soft_material_source(
        vec2 point, vec2 center, vec2 radius, float edge_variation) {
    float organic_distance = length((point - center) / radius) + edge_variation;
    return 1.0 - smoothstep(0.48, 1.0, organic_distance);
}

float soft_material_shell(
        vec2 point, vec2 center, vec2 radius, float edge_variation) {
    float outer_source = soft_material_source(point, center, radius, edge_variation);
    float inner_source = soft_material_source(
        point, center, radius * 0.54, edge_variation * 0.55
    );
    return max(outer_source - inner_source * 0.86, 0.0);
}

vec4 reset_composition(vec2 uv, float aspect) {
    vec2 point = vec2((uv.x - 0.5) * aspect, uv.y - 0.5);
    point += vec2(u_motion.x * 0.022, u_motion.y * 0.016);
    float broad_variation = value_noise(point * 1.25 + 4.7) - 0.5;
    float fine_variation = value_noise(point * 2.4 + 17.3) - 0.5;

    float plume_axis = point.y + 0.11 - point.x * 0.15 + broad_variation * 0.12;
    float plume_extent = 1.0 - smoothstep(
        -0.38, 0.20, point.x + fine_variation * 0.055
    );
    float left_luminous_plume = soft_band(plume_axis, 0.0, 0.34) * plume_extent;

    float density = clamp(0.40 + left_luminous_plume * 0.42
        + broad_variation * 0.12 + fine_variation * 0.06, 0.24, 1.0);
    float balanced_seed_depth = 0.18 + (broad_variation + 0.5) * 0.10
        + (1.0 - left_luminous_plume) * 0.04;
    float depth = clamp(balanced_seed_depth, 0.12, 0.30);
    return vec4(density, depth, clamp(0.5 + fine_variation, 0.0, 1.0), 0.0);
}

void main() {
    float delta_time = clamp(u_delta_time, 0.0, 0.0333333);
    vec2 velocity = velocity_at(v_uv) + AMBIENT_TRANSPORT_UV;
    vec2 midpoint_uv = clamp_uv(v_uv - velocity * delta_time * 0.5);
    vec2 midpoint_velocity = velocity_at(midpoint_uv) + AMBIENT_TRANSPORT_UV;
    vec2 backtrace_uv = clamp_uv(v_uv - midpoint_velocity * delta_time);
    vec4 advected_carrier = texture(u_carrier, backtrace_uv);

    float aspect = u_texel.y / max(u_texel.x, 0.000001);
    vec2 point = vec2((v_uv.x - 0.5) * aspect, v_uv.y - 0.5);
    float inflow_shape = soft_band(point.y + point.x * 0.10, -0.06, 0.31);
    float edge_inflow = (1.0 - smoothstep(-0.58, -0.40, point.x)) * inflow_shape;
    vec2 primary_path = u_motion.xy;
    vec2 secondary_path = u_motion.zw;
    float center_edge = (value_noise(point * 1.10 + secondary_path * 0.24 + 7.6) - 0.5) * 0.24;
    float right_edge = (value_noise(point * 1.18 - secondary_path * 0.20 + 31.4) - 0.5) * 0.22;
    vec2 center_position = vec2(
        -0.04 + primary_path.x * 0.16 + secondary_path.x * 0.06,
        -0.08 + primary_path.y * 0.10 + secondary_path.y * 0.05
    );
    vec2 right_position = vec2(
        0.40 - primary_path.y * 0.15 + secondary_path.x * 0.07,
        0.10 + primary_path.x * 0.10 - secondary_path.y * 0.05
    );
    float moving_center_source = soft_material_shell(
        point, center_position, vec2(0.40, 0.28), center_edge
    );
    float moving_right_source = soft_material_shell(
        point, right_position, vec2(0.34, 0.24), right_edge
    );

    float density = advected_carrier.r / (1.0 + 0.055 * delta_time);
    float sediment = advected_carrier.g / (1.0 + 0.025 * delta_time);
    density = clamp(density + edge_inflow * delta_time * 0.22, 0.0, 1.0);
    sediment = clamp(sediment + edge_inflow * delta_time * 0.035, 0.0, 1.0);
    vec2 inflow_drift = vec2(u_motion.x * 0.28, u_motion.y * 0.17);
    float inflow_variation = value_noise(point * 1.4 + inflow_drift + 19.2);
    float variation_injection = edge_inflow * delta_time * 1.50;
    float material_variation = mix(
        advected_carrier.b, inflow_variation, clamp(variation_injection, 0.0, 1.0)
    );
    float center_material_target = clamp(
        0.68 + (value_noise(point * 1.30 + secondary_path * 0.26 + 43.7) - 0.5) * 0.18,
        0.0,
        1.0
    );
    float right_material_target = clamp(
        0.32 + (value_noise(point * 1.24 - secondary_path * 0.23 + 71.2) - 0.5) * 0.18,
        0.0,
        1.0
    );
    float source_sum = moving_center_source + moving_right_source;
    float interior_material_target = (
        center_material_target * moving_center_source
            + right_material_target * moving_right_source
    ) / max(source_sum, 0.0001);
    float interior_injection = clamp(source_sum * delta_time * 1.35, 0.0, 1.0);
    material_variation = mix(material_variation, interior_material_target, interior_injection);
    float material_age = min(1.0, advected_carrier.a + delta_time * (0.018 + density * 0.012));
    vec4 evolved = vec4(density, sediment, material_variation, material_age);
    o_color = mix(evolved, reset_composition(v_uv, aspect), step(0.5, u_reset));
}
