package top.boluofan.musictv.ui.background;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Test;

public class FluidShaderSourceTest {
    private static final String FULLSCREEN_VERTEX = "fluid_background_vertex.glsl";
    private static final String VELOCITY = "fluid_background_update_fragment.glsl";
    private static final String FINAL = "fluid_background_fragment.glsl";
    private static final Pattern SPATIAL_SINE = Pattern.compile(
            "sin\\s*\\([^;\\n]*(?:field|point|uv|coord|position)");
    private static final Pattern BATCHED_TIME_SEED = Pattern.compile(
            "floor\\s*\\([^)]*(?:u_time|time|frame|phase)");
    private static final Pattern GLOBAL_WHITE_MIX = Pattern.compile(
            "mix\\s*\\([^,]+,\\s*(?:vec3\\s*\\(\\s*1(?:\\.0+)?|white)".toLowerCase(Locale.ROOT));

    @Test
    public void suiteUsesGlsl300AndHighPrecision() throws IOException {
        for (String fileName : shaderContracts().keySet()) {
            String shader = shaderSource(fileName);
            assertTrue(fileName, shader.startsWith("#version 300 es"));
            assertTrue(fileName, shader.contains("precision highp float"));
            assertFalse(fileName, shader.contains("texture2d"));
            assertFalse(fileName, shader.contains("gl_fragcolor"));
            assertFalse(fileName, shader.contains("attribute "));
            assertFalse(fileName, shader.contains("varying "));
        }
    }

    @Test
    public void everyProgramExposesTheSharedUniformContract() throws IOException {
        for (Map.Entry<String, String[]> contract : shaderContracts().entrySet()) {
            String shader = shaderSource(contract.getKey());
            for (String declaration : contract.getValue()) {
                assertTrue(contract.getKey() + " missing " + declaration,
                        shader.contains(declaration));
            }
        }
    }

    @Test
    public void fullscreenAndTransformFeedbackAbiAreStable() throws IOException {
        String fullscreen = shaderSource(FULLSCREEN_VERTEX);
        assertTrue(fullscreen.contains("layout(location = 0) in vec2 a_position"));
        assertTrue(fullscreen.contains("out vec2 v_uv"));

        String particle = shaderSource("fluid_particle_update_vertex.glsl");
        assertTrue(particle.contains("layout(location = 0) in vec4 a_position_velocity"));
        assertTrue(particle.contains("layout(location = 1) in vec4 a_material"));
        assertTrue(particle.contains("layout(location = 2) in vec4 a_identity"));
        assertTrue(particle.contains("out vec4 v_position_velocity"));
        assertTrue(particle.contains("out vec4 v_material"));
        assertTrue(particle.contains("out vec4 v_identity"));
        assertTrue(particle.contains("stable_seed"));
        assertTrue(particle.contains("respawn_cycle"));
        assertTrue(particle.contains("flow_response"));
        assertFalse(particle.contains("settle_threshold"));
        assertFalse(particle.contains("erosion_threshold"));
    }

    @Test
    public void carrierSuiteProjectsVelocityAndKeepsMaterialBroad() throws IOException {
        String velocity = shaderSource(VELOCITY);
        String divergence = shaderSource("fluid_divergence_fragment.glsl");
        String pressure = shaderSource("fluid_pressure_fragment.glsl");
        String projection = shaderSource("fluid_projection_fragment.glsl");
        String carrier = shaderSource("fluid_carrier_fragment.glsl");

        assertTrue(velocity.contains("midpoint_uv"));
        assertTrue(velocity.contains("offscreen_return"));
        assertTrue(velocity.contains("upper_right_curl"));
        assertTrue(velocity.contains("concentration_damping"));
        assertTrue(divergence.contains("velocity_right.x - velocity_left.x"));
        assertTrue(pressure.contains("pressure_left + pressure_right"));
        assertTrue(projection.contains("pressure_gradient"));
        assertTrue(projection.contains("projected_velocity"));
        assertTrue(carrier.contains("advected_carrier"));
        assertTrue(carrier.contains("ambient_transport_uv"));
        assertTrue(carrier.contains("reset_composition"));
        assertTrue(carrier.contains("left_luminous_plume"));
        assertTrue(carrier.contains("balanced_seed_depth"));
        assertTrue(carrier.contains("moving_center_source"));
        assertTrue(carrier.contains("moving_right_source"));
        assertTrue(carrier.contains("soft_material_shell"));
        assertTrue(carrier.contains("center_material_target"));
        assertTrue(carrier.contains("right_material_target"));
        assertFalse(carrier.contains("center_right_trench"));
        assertFalse(carrier.contains("right_dark_field"));
        assertFalse(carrier.contains("maccormack"));
        assertFalse(carrier.contains("bfecc"));
    }

    @Test
    public void particleDepositAndSparklesStayMaterialAttached() throws IOException {
        String particle = shaderSource("fluid_particle_update_vertex.glsl");
        String splatVertex = shaderSource("fluid_particle_splat_vertex.glsl");
        String splatFragment = shaderSource("fluid_particle_splat_fragment.glsl");
        String resolve = shaderSource("fluid_deposit_resolve_fragment.glsl");
        String emissiveVertex = shaderSource("fluid_particle_emissive_vertex.glsl");
        String emissiveFragment = shaderSource("fluid_particle_emissive_fragment.glsl");

        assertTrue(particle.contains("flow_response"));
        assertTrue(particle.contains("ambient_transport_uv"));
        assertFalse(particle.contains("gravity"));
        assertFalse(particle.contains("settled"));
        assertTrue(splatVertex.contains("out vec4 v_splat_data"));
        assertTrue(splatVertex.contains("flow_response"));
        assertFalse(splatVertex.contains("settled"));
        assertTrue(splatFragment.contains("gl_pointcoord"));
        assertFalse(splatFragment.contains("settled"));
        assertTrue(resolve.contains("history_relaxation"));
        assertTrue(resolve.contains("shear_erosion"));
        assertTrue(emissiveVertex.contains("texture(u_deposit"));
        assertTrue(emissiveVertex.contains("texture(u_carrier"));
        assertTrue(emissiveVertex.contains("carrier_curvature"));
        assertTrue(emissiveVertex.contains("stable_selection"));
        assertTrue(emissiveVertex.contains("continuous_breath"));
        assertTrue(emissiveVertex.contains("a_identity.x"));
        assertTrue(emissiveFragment.contains("gl_pointcoord"));
        assertTrue(emissiveFragment.contains("cool_white"));
        assertTrue(emissiveFragment.contains("mint_white"));
        assertFalse(emissiveFragment.contains("gold"));
    }

    @Test
    public void bloomIsIsolatedAndFinalCompositeEncodesSrgb() throws IOException {
        String scene = shaderSource("fluid_scene_fragment.glsl");
        String downsample = shaderSource("fluid_downsample_fragment.glsl");
        String blur = shaderSource("fluid_blur_fragment.glsl");
        String composite = shaderSource(FINAL);

        assertTrue(scene.contains("carrier_gradient"));
        assertTrue(scene.contains("deposit_gradient"));
        assertTrue(scene.contains("carrier_form_gradient"));
        assertTrue(scene.contains("deposit_form_gradient"));
        assertTrue(scene.contains("transmission_mask"));
        assertTrue(scene.contains("narrow_crest"));
        assertTrue(scene.contains("srgb_to_linear"));
        assertFalse(scene.contains("bloom"));
        assertTrue(downsample.contains("texture(u_source"));
        assertTrue(blur.contains("u_direction"));
        assertTrue(composite.contains("texture(u_emissive"));
        assertTrue(composite.contains("texture(u_bloom_near"));
        assertTrue(composite.contains("texture(u_bloom_far"));
        assertTrue(composite.contains("linear_to_srgb"));
        assertTrue(composite.contains("compress_hdr_glow"));
        assertTrue(composite.contains("0.24"));
        assertTrue(composite.contains("0.12"));
        assertFalse(composite.contains("tone_map(scene"));
        assertFalse(composite.contains("u_carrier"));
        assertFalse(composite.contains("u_deposit"));
    }

    @Test
    public void sceneMakesTransportedMaterialVisibleWithoutWhiteWash() throws IOException {
        String scene = shaderSource("fluid_scene_fragment.glsl");

        assertTrue(scene.contains("material_tracer"));
        assertTrue(scene.contains("carrier.b"));
        assertTrue(scene.contains("tracer_shadow"));
        assertTrue(scene.contains("u_carrier_texel * 8.0"));
        assertTrue(scene.contains("carrier_detail * 0.05"));
        assertTrue(scene.contains("vec3(0.282, 0.663, 0.506)"));
        assertTrue(scene.contains("vec3(0.714, 0.914, 0.831)"));
        assertFalse(scene.contains("vec3(1.0)"));
    }

    @Test
    public void suiteAvoidsDetachedProceduralDetailAndWhiteWash() throws IOException {
        StringBuilder combined = new StringBuilder();
        for (String fileName : shaderContracts().keySet()) {
            combined.append(shaderSource(fileName)).append('\n');
        }
        String source = combined.toString();

        assertFalse(source.contains("gl_fragcoord"));
        assertFalse(source.contains("screen_hash"));
        assertFalse(source.contains("sparkle_hash"));
        assertFalse(source.contains("ridge"));
        assertFalse(source.contains("filament"));
        assertFalse(source.contains("billow"));
        assertFalse(source.contains("spatial_sine"));
        assertFalse(SPATIAL_SINE.matcher(source).find());
        assertFalse(BATCHED_TIME_SEED.matcher(source).find());
        assertFalse(GLOBAL_WHITE_MIX.matcher(source).find());
    }

    private Map<String, String[]> shaderContracts() {
        Map<String, String[]> contracts = new LinkedHashMap<>();
        contracts.put(FULLSCREEN_VERTEX, new String[] {});
        contracts.put(VELOCITY, new String[] {
                "uniform sampler2d u_velocity", "uniform sampler2d u_carrier",
                "uniform vec2 u_texel", "uniform float u_delta_time", "uniform vec4 u_motion"});
        contracts.put("fluid_divergence_fragment.glsl", new String[] {
                "uniform sampler2d u_velocity", "uniform vec2 u_texel"});
        contracts.put("fluid_pressure_fragment.glsl", new String[] {
                "uniform sampler2d u_pressure", "uniform sampler2d u_divergence",
                "uniform vec2 u_texel"});
        contracts.put("fluid_projection_fragment.glsl", new String[] {
                "uniform sampler2d u_velocity", "uniform sampler2d u_pressure",
                "uniform vec2 u_texel"});
        contracts.put("fluid_carrier_fragment.glsl", new String[] {
                "uniform sampler2d u_carrier", "uniform sampler2d u_velocity",
                "uniform vec2 u_texel", "uniform float u_delta_time", "uniform vec4 u_motion",
                "uniform float u_reset"});
        contracts.put("fluid_particle_update_vertex.glsl", new String[] {
                "uniform sampler2d u_velocity", "uniform vec2 u_velocity_texel",
                "uniform float u_delta_time", "uniform vec4 u_motion"});
        contracts.put("fluid_particle_discard_fragment.glsl", new String[] {});
        contracts.put("fluid_particle_splat_vertex.glsl", new String[] {
                "uniform vec2 u_deposit_resolution"});
        contracts.put("fluid_particle_splat_fragment.glsl", new String[] {});
        contracts.put("fluid_deposit_resolve_fragment.glsl", new String[] {
                "uniform sampler2d u_transient", "uniform sampler2d u_history",
                "uniform sampler2d u_velocity", "uniform vec2 u_velocity_texel",
                "uniform float u_delta_time"});
        contracts.put("fluid_scene_fragment.glsl", new String[] {
                "uniform sampler2d u_carrier", "uniform sampler2d u_deposit",
                "uniform vec2 u_surface_resolution", "uniform vec2 u_carrier_texel",
                "uniform vec2 u_deposit_texel"});
        contracts.put("fluid_particle_emissive_vertex.glsl", new String[] {
                "uniform vec2 u_surface_resolution", "uniform sampler2d u_deposit",
                "uniform sampler2d u_carrier", "uniform vec2 u_carrier_texel",
                "uniform float u_time"});
        contracts.put("fluid_particle_emissive_fragment.glsl", new String[] {});
        contracts.put("fluid_downsample_fragment.glsl", new String[] {
                "uniform sampler2d u_source", "uniform vec2 u_source_texel",
                "uniform vec2 u_source_scale"});
        contracts.put("fluid_blur_fragment.glsl", new String[] {
                "uniform sampler2d u_source", "uniform vec2 u_source_texel",
                "uniform vec2 u_direction"});
        contracts.put(FINAL, new String[] {
                "uniform sampler2d u_scene", "uniform sampler2d u_emissive",
                "uniform sampler2d u_bloom_near", "uniform sampler2d u_bloom_far"});
        return contracts;
    }

    private String shaderSource(String fileName) throws IOException {
        Path modulePath = Paths.get("src/main/res/raw").resolve(fileName);
        Path rootPath = Paths.get("app").resolve(modulePath);
        Path shaderPath = Files.exists(modulePath) ? modulePath : rootPath;
        String source = new String(Files.readAllBytes(shaderPath), StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT);
        return source.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
