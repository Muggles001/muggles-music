package top.boluofan.musictv.ui.background;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import top.boluofan.musictv.R;

final class FluidBackgroundRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "FluidBackground";
    private static final float FALLBACK_RED = 241f / 255f;
    private static final float FALLBACK_GREEN = 249f / 255f;
    private static final float FALLBACK_BLUE = 244f / 255f;
    private static final float[] FULLSCREEN_QUAD = {
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f
    };
    private static final String[] PARTICLE_ATTRIBUTES = {
            "a_position_velocity", "a_material", "a_identity"
    };
    private static final String[] PARTICLE_VARYINGS = {
            "v_position_velocity", "v_material", "v_identity"
    };

    private final Context context;
    private final FluidBackgroundPolicy.Profile profile;
    private final Runnable unavailableCallback;
    private final FluidSimulationClock simulationClock;
    private final FluidPingPongState velocityState = new FluidPingPongState();
    private final FluidPingPongState pressureState = new FluidPingPongState();
    private final FluidPingPongState carrierState = new FluidPingPongState();
    private final FluidPingPongState depositState = new FluidPingPongState();
    private final FluidPingPongState particleState = new FluidPingPongState();

    private final RenderTarget[] velocityTargets = RenderTarget.createPair();
    private final RenderTarget[] pressureTargets = RenderTarget.createPair();
    private final RenderTarget divergenceTarget = new RenderTarget();
    private final RenderTarget[] carrierTargets = RenderTarget.createPair();
    private final RenderTarget transientDepositTarget = new RenderTarget();
    private final RenderTarget[] depositTargets = RenderTarget.createPair();
    private final RenderTarget sceneTarget = new RenderTarget();
    private final RenderTarget emissiveTarget = new RenderTarget();
    private final RenderTarget[] bloomNearTargets = RenderTarget.createPair();
    private final RenderTarget[] bloomFarTargets = RenderTarget.createPair();

    private final int[] particleBuffers = new int[2];
    private final int[] particleVaos = new int[2];
    private final int[] particleTransformFeedbacks = new int[2];

    private ShaderProgram velocityProgram;
    private ShaderProgram divergenceProgram;
    private ShaderProgram pressureProgram;
    private ShaderProgram projectionProgram;
    private ShaderProgram carrierProgram;
    private ShaderProgram particleUpdateProgram;
    private ShaderProgram particleSplatProgram;
    private ShaderProgram depositProgram;
    private ShaderProgram sceneProgram;
    private ShaderProgram particleEmissiveProgram;
    private ShaderProgram downsampleProgram;
    private ShaderProgram blurProgram;
    private ShaderProgram finalProgram;

    private int fullscreenVao;
    private int fullscreenBuffer;
    private int surfaceWidth;
    private int surfaceHeight;
    private boolean resourcesReady;
    private boolean surfaceTargetsReady;
    private boolean unavailableSignaled;
    private boolean initializationPending;
    private boolean fallbackFramePresented;

    FluidBackgroundRenderer(
            Context context,
            FluidBackgroundPolicy.Profile profile,
            Runnable unavailableCallback) {
        this.context = context;
        this.profile = profile;
        this.unavailableCallback = unavailableCallback;
        simulationClock = new FluidSimulationClock(
                profile.simulationStepNanos, profile.maxSimulationStepsPerFrame);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        setFallbackClearColor();
        invalidateContextResources();
        simulationClock.resetWallTime(SystemClock.elapsedRealtimeNanos());
        initializationPending = !unavailableSignaled;
        fallbackFramePresented = false;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        GLES30.glViewport(0, 0, width, height);
        if (!resourcesReady || unavailableSignaled) return;
        if (surfaceTargetsReady && sceneTarget.width == width && sceneTarget.height == height) {
            return;
        }
        try {
            deleteSurfaceTargets();
            createSurfaceTargets(width, height);
            surfaceTargetsReady = true;
            checkGlError("surface target initialization");
        } catch (RuntimeException error) {
            fail("Unable to allocate fluid surface targets", error);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight);
        setFallbackClearColor();
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        if (unavailableSignaled) return;
        if (initializationPending) {
            // Keep the opaque Surface on a mint frame while expensive GL resources initialize.
            if (!fallbackFramePresented) {
                fallbackFramePresented = true;
                return;
            }
            try {
                initializeContextResources();
                initializationPending = false;
            } catch (RuntimeException | IOException error) {
                fail("High-fidelity fluid renderer unavailable", error);
                return;
            }
        }
        if (!resourcesReady || !surfaceTargetsReady) return;

        try {
            long nowNanos = SystemClock.elapsedRealtimeNanos();
            int stepCount = simulationClock.advance(nowNanos);
            double finalTime = simulationClock.simulationTimeSeconds();
            double stepSeconds = simulationClock.stepSeconds();
            for (int step = 0; step < stepCount; step++) {
                double stepTime = finalTime - (stepCount - step - 1) * stepSeconds;
                runSimulationStep(stepTime);
            }
            renderSurface(finalTime);
            checkGlError("frame rendering");
        } catch (RuntimeException error) {
            fail("Fluid rendering failed", error);
        }
    }

    void resetWallClock() {
        simulationClock.resetWallTime(SystemClock.elapsedRealtimeNanos());
    }

    private void initializeContextResources() throws IOException {
        verifyCapabilities();
        createPrograms();
        createFullscreenGeometry();
        createSimulationTargets();
        createParticleResources();
        clearSimulationState();
        seedCarrierTargets();
        resourcesReady = true;
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            createSurfaceTargets(surfaceWidth, surfaceHeight);
            surfaceTargetsReady = true;
        }
        checkGlError("resource initialization");
    }

    private void verifyCapabilities() {
        int[] value = new int[1];
        GLES30.glGetIntegerv(GLES30.GL_MAJOR_VERSION, value, 0);
        if (value[0] < 3) throw new IllegalStateException("OpenGL ES 3.0 is required");

        int[] precisionRange = new int[2];
        int[] precision = new int[1];
        GLES30.glGetShaderPrecisionFormat(GLES30.GL_FRAGMENT_SHADER, GLES30.GL_HIGH_FLOAT,
                precisionRange, 0, precision, 0);
        if (precision[0] <= 0) {
            throw new IllegalStateException("High precision fragment arithmetic is required");
        }

        String extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS);
        boolean halfFloatRenderTarget = hasExtension(
                extensions, "GL_EXT_color_buffer_half_float")
                || hasExtension(extensions, "GL_EXT_color_buffer_float");
        if (!halfFloatRenderTarget) {
            throw new IllegalStateException("Renderable half-float textures are required");
        }

        GLES30.glGetIntegerv(GLES30.GL_MAX_DRAW_BUFFERS, value, 0);
        if (value[0] < 1) throw new IllegalStateException("A draw buffer is required");
        GLES30.glGetIntegerv(GLES30.GL_MAX_TRANSFORM_FEEDBACK_INTERLEAVED_COMPONENTS, value, 0);
        if (value[0] < FluidRendererPlan.PARTICLE_FLOAT_COUNT) {
            throw new IllegalStateException("Transform-feedback particle ABI is unsupported");
        }
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_IMAGE_UNITS, value, 0);
        if (value[0] < 4) throw new IllegalStateException("Four texture units are required");
    }

    private static boolean hasExtension(String extensions, String required) {
        if (extensions == null) return false;
        for (String extension : extensions.split(" ")) {
            if (required.equals(extension)) return true;
        }
        return false;
    }

    private void createPrograms() throws IOException {
        String fullscreenVertex = readRawResource(R.raw.fluid_background_vertex);
        velocityProgram = createFullscreenProgram(fullscreenVertex,
                R.raw.fluid_background_update_fragment,
                "u_velocity", "u_carrier", "u_texel", "u_delta_time", "u_motion");
        divergenceProgram = createFullscreenProgram(fullscreenVertex,
                R.raw.fluid_divergence_fragment, "u_velocity", "u_texel");
        pressureProgram = createFullscreenProgram(fullscreenVertex,
                R.raw.fluid_pressure_fragment, "u_pressure", "u_divergence", "u_texel");
        projectionProgram = createFullscreenProgram(fullscreenVertex,
                R.raw.fluid_projection_fragment, "u_velocity", "u_pressure", "u_texel");
        carrierProgram = createFullscreenProgram(fullscreenVertex,
                R.raw.fluid_carrier_fragment,
                "u_carrier", "u_velocity", "u_texel", "u_delta_time", "u_motion", "u_reset");
        particleUpdateProgram = createParticleProgram(
                readRawResource(R.raw.fluid_particle_update_vertex),
                readRawResource(R.raw.fluid_particle_discard_fragment),
                PARTICLE_VARYINGS,
                3,
                "u_velocity", "u_velocity_texel", "u_delta_time", "u_motion");
        particleSplatProgram = createParticleProgram(
                readRawResource(R.raw.fluid_particle_splat_vertex),
                readRawResource(R.raw.fluid_particle_splat_fragment), null, 2,
                "u_deposit_resolution");
        depositProgram = createFullscreenProgram(fullscreenVertex,
                R.raw.fluid_deposit_resolve_fragment,
                "u_transient", "u_history", "u_velocity",
                "u_velocity_texel", "u_delta_time");
        sceneProgram = createFullscreenProgram(fullscreenVertex,
                R.raw.fluid_scene_fragment,
                "u_carrier", "u_deposit", "u_surface_resolution",
                "u_carrier_texel", "u_deposit_texel");
        particleEmissiveProgram = createParticleProgram(
                readRawResource(R.raw.fluid_particle_emissive_vertex),
                readRawResource(R.raw.fluid_particle_emissive_fragment), null, 3,
                "u_surface_resolution", "u_deposit", "u_carrier",
                "u_carrier_texel", "u_time");
        downsampleProgram = createFullscreenProgram(fullscreenVertex,
                R.raw.fluid_downsample_fragment,
                "u_source", "u_source_texel", "u_source_scale");
        blurProgram = createFullscreenProgram(fullscreenVertex,
                R.raw.fluid_blur_fragment, "u_source", "u_source_texel", "u_direction");
        finalProgram = createFullscreenProgram(fullscreenVertex,
                R.raw.fluid_background_fragment,
                "u_scene", "u_emissive", "u_bloom_near", "u_bloom_far");
    }

    private ShaderProgram createFullscreenProgram(
            String vertexSource, int fragmentResource, String... uniforms) throws IOException {
        ShaderProgram program = createProgram(
                vertexSource, readRawResource(fragmentResource), null, uniforms);
        int position = GLES30.glGetAttribLocation(program.id, "a_position");
        if (position != 0) {
            GLES30.glDeleteProgram(program.id);
            throw new IllegalStateException("Fullscreen attribute must use location zero");
        }
        return program;
    }

    private ShaderProgram createParticleProgram(
            String vertexSource,
            String fragmentSource,
            String[] transformFeedbackVaryings,
            int requiredAttributeCount,
            String... uniforms) {
        ShaderProgram program = createProgram(
                vertexSource, fragmentSource, transformFeedbackVaryings, uniforms);
        for (int attribute = 0; attribute < requiredAttributeCount; attribute++) {
            if (GLES30.glGetAttribLocation(program.id, PARTICLE_ATTRIBUTES[attribute]) != attribute) {
                GLES30.glDeleteProgram(program.id);
                throw new IllegalStateException("Particle attribute ABI is incomplete");
            }
        }
        return program;
    }

    private static ShaderProgram createProgram(
            String vertexSource,
            String fragmentSource,
            String[] transformFeedbackVaryings,
            String... uniforms) {
        int vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader;
        try {
            fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource);
        } catch (RuntimeException error) {
            GLES30.glDeleteShader(vertexShader);
            throw error;
        }
        int programId = GLES30.glCreateProgram();
        if (programId == 0) {
            GLES30.glDeleteShader(vertexShader);
            GLES30.glDeleteShader(fragmentShader);
            throw new IllegalStateException("Unable to create fluid shader program");
        }
        GLES30.glAttachShader(programId, vertexShader);
        GLES30.glAttachShader(programId, fragmentShader);
        if (transformFeedbackVaryings != null) {
            GLES30.glTransformFeedbackVaryings(
                    programId, transformFeedbackVaryings, GLES30.GL_INTERLEAVED_ATTRIBS);
        }
        GLES30.glLinkProgram(programId);
        int[] status = new int[1];
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, status, 0);
        GLES30.glDeleteShader(vertexShader);
        GLES30.glDeleteShader(fragmentShader);
        if (status[0] == 0) {
            String message = GLES30.glGetProgramInfoLog(programId);
            GLES30.glDeleteProgram(programId);
            throw new IllegalStateException("Unable to link fluid shader: " + message);
        }
        return new ShaderProgram(programId, uniforms);
    }

    private static int compileShader(int type, String source) {
        int shader = GLES30.glCreateShader(type);
        if (shader == 0) throw new IllegalStateException("Unable to create fluid shader");
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);
        int[] status = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String message = GLES30.glGetShaderInfoLog(shader);
            GLES30.glDeleteShader(shader);
            throw new IllegalStateException("Unable to compile fluid shader: " + message);
        }
        return shader;
    }

    private void createFullscreenGeometry() {
        int[] names = new int[1];
        GLES30.glGenVertexArrays(1, names, 0);
        fullscreenVao = names[0];
        GLES30.glGenBuffers(1, names, 0);
        fullscreenBuffer = names[0];
        if (fullscreenVao == 0 || fullscreenBuffer == 0) {
            throw new IllegalStateException("Unable to allocate fullscreen geometry");
        }
        FloatBuffer vertices = ByteBuffer.allocateDirect(FULLSCREEN_QUAD.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertices.put(FULLSCREEN_QUAD).position(0);
        GLES30.glBindVertexArray(fullscreenVao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, fullscreenBuffer);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,
                FULLSCREEN_QUAD.length * Float.BYTES, vertices, GLES30.GL_STATIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0);
        GLES30.glBindVertexArray(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    private void createSimulationTargets() {
        createHalfFloatPair(velocityTargets, profile.carrierWidth, profile.carrierHeight);
        createHalfFloatPair(pressureTargets, profile.carrierWidth, profile.carrierHeight);
        createTarget(divergenceTarget,
                profile.carrierWidth, profile.carrierHeight, GLES30.GL_RGBA16F, GLES30.GL_HALF_FLOAT);
        createHalfFloatPair(carrierTargets, profile.carrierWidth, profile.carrierHeight);
        createTarget(transientDepositTarget,
                profile.depositWidth, profile.depositHeight, GLES30.GL_RGBA8, GLES30.GL_UNSIGNED_BYTE);
        createHalfFloatPair(depositTargets, profile.depositWidth, profile.depositHeight);
    }

    private void createSurfaceTargets(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Surface dimensions must be positive");
        }
        createTarget(sceneTarget, width, height, GLES30.GL_RGBA8, GLES30.GL_UNSIGNED_BYTE);
        createTarget(emissiveTarget, width, height, GLES30.GL_RGBA8, GLES30.GL_UNSIGNED_BYTE);
        int nearWidth = FluidRendererPlan.bloomDimension(width, 4);
        int nearHeight = FluidRendererPlan.bloomDimension(height, 4);
        createBytePair(bloomNearTargets, nearWidth, nearHeight);
        int farWidth = FluidRendererPlan.bloomDimension(width, 8);
        int farHeight = FluidRendererPlan.bloomDimension(height, 8);
        createBytePair(bloomFarTargets, farWidth, farHeight);
        clearTarget(sceneTarget);
        clearTarget(emissiveTarget);
        clearTargets(bloomNearTargets);
        clearTargets(bloomFarTargets);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
    }

    private void createHalfFloatPair(RenderTarget[] targets, int width, int height) {
        for (RenderTarget target : targets) {
            createTarget(target, width, height, GLES30.GL_RGBA16F, GLES30.GL_HALF_FLOAT);
        }
    }

    private void createBytePair(RenderTarget[] targets, int width, int height) {
        for (RenderTarget target : targets) {
            createTarget(target, width, height, GLES30.GL_RGBA8, GLES30.GL_UNSIGNED_BYTE);
        }
    }

    private static void createTarget(
            RenderTarget target, int width, int height, int internalFormat, int type) {
        int[] names = new int[1];
        GLES30.glGenTextures(1, names, 0);
        target.texture = names[0];
        GLES30.glGenFramebuffers(1, names, 0);
        target.framebuffer = names[0];
        target.width = width;
        target.height = height;
        if (target.texture == 0 || target.framebuffer == 0) {
            throw new IllegalStateException("Unable to allocate fluid render target");
        }
        drainGlErrors();
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, target.texture);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, internalFormat,
                width, height, 0, GLES30.GL_RGBA, type, null);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.framebuffer);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, target.texture, 0);
        int status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException(
                    "Fluid framebuffer is incomplete: 0x" + Integer.toHexString(status));
        }
        checkGlError("render-target allocation");
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
    }

    private void createParticleResources() {
        float[] initialData = FluidRendererPlan.createInitialParticles(profile.particleCount);
        FloatBuffer initialBuffer = ByteBuffer.allocateDirect(initialData.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        initialBuffer.put(initialData).position(0);
        int bufferSize = profile.particleCount * FluidRendererPlan.PARTICLE_STRIDE_BYTES;

        GLES30.glGenBuffers(2, particleBuffers, 0);
        GLES30.glGenVertexArrays(2, particleVaos, 0);
        GLES30.glGenTransformFeedbacks(2, particleTransformFeedbacks, 0);
        for (int index = 0; index < 2; index++) {
            if (particleBuffers[index] == 0 || particleVaos[index] == 0
                    || particleTransformFeedbacks[index] == 0) {
                throw new IllegalStateException("Unable to allocate particle resources");
            }
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, particleBuffers[index]);
            initialBuffer.position(0);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,
                    bufferSize, initialBuffer, GLES30.GL_DYNAMIC_COPY);

            GLES30.glBindVertexArray(particleVaos[index]);
            for (int attribute = 0; attribute < 3; attribute++) {
                GLES30.glEnableVertexAttribArray(attribute);
                GLES30.glVertexAttribPointer(attribute, 4, GLES30.GL_FLOAT, false,
                        FluidRendererPlan.PARTICLE_STRIDE_BYTES, attribute * 4 * Float.BYTES);
            }

            GLES30.glBindTransformFeedback(
                    GLES30.GL_TRANSFORM_FEEDBACK, particleTransformFeedbacks[index]);
            GLES30.glBindBufferBase(
                    GLES30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, particleBuffers[index]);
        }
        GLES30.glBindTransformFeedback(GLES30.GL_TRANSFORM_FEEDBACK, 0);
        GLES30.glBindVertexArray(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glBindBuffer(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER, 0);
    }

    private void clearSimulationState() {
        clearTargets(velocityTargets);
        clearTargets(pressureTargets);
        clearTarget(divergenceTarget);
        clearTargets(carrierTargets);
        clearTarget(transientDepositTarget);
        clearTargets(depositTargets);
        velocityState.reset();
        pressureState.reset();
        carrierState.reset();
        depositState.reset();
        particleState.reset();
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        setFallbackClearColor();
    }

    private void seedCarrierTargets() {
        double seedTimeSeconds = simulationClock.simulationTimeSeconds();
        for (int targetIndex = 0; targetIndex < carrierTargets.length; targetIndex++) {
            RenderTarget target = carrierTargets[targetIndex];
            beginFullscreenPass(carrierProgram, target);
            bindTexture(0, carrierTargets[1 - targetIndex].texture,
                    carrierProgram.uniform("u_carrier"));
            bindTexture(1, velocityTargets[0].texture,
                    carrierProgram.uniform("u_velocity"));
            setCarrierTexel(carrierProgram.uniform("u_texel"));
            GLES30.glUniform1f(carrierProgram.uniform("u_delta_time"), 0f);
            setMotion(carrierProgram.uniform("u_motion"), seedTimeSeconds);
            GLES30.glUniform1f(carrierProgram.uniform("u_reset"), 1f);
            drawFullscreen();
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        setFallbackClearColor();
    }

    private static void clearTargets(RenderTarget[] targets) {
        for (RenderTarget target : targets) clearTarget(target);
    }

    private static void clearTarget(RenderTarget target) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.framebuffer);
        GLES30.glViewport(0, 0, target.width, target.height);
        GLES30.glClearColor(0f, 0f, 0f, 0f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
    }

    private void runSimulationStep(double simulationTimeSeconds) {
        runVelocityPass(simulationTimeSeconds);
        runDivergencePass();
        for (int iteration = 0; iteration < profile.pressureIterations; iteration++) {
            runPressurePass();
        }
        runProjectionPass();
        runCarrierPass(simulationTimeSeconds);
        runParticleUpdatePass(simulationTimeSeconds);
        runParticleSplatPass();
        runDepositPass();
    }

    private void runVelocityPass(double simulationTimeSeconds) {
        RenderTarget target = velocityTargets[velocityState.writeIndex()];
        beginFullscreenPass(velocityProgram, target);
        bindTexture(0, velocityTargets[velocityState.readIndex()].texture,
                velocityProgram.uniform("u_velocity"));
        bindTexture(1, carrierTargets[carrierState.readIndex()].texture,
                velocityProgram.uniform("u_carrier"));
        setCarrierTexel(velocityProgram.uniform("u_texel"));
        GLES30.glUniform1f(velocityProgram.uniform("u_delta_time"),
                profile.simulationStepSeconds());
        setMotion(velocityProgram.uniform("u_motion"), simulationTimeSeconds);
        drawFullscreen();
        velocityState.swap();
    }

    private void runDivergencePass() {
        beginFullscreenPass(divergenceProgram, divergenceTarget);
        bindTexture(0, velocityTargets[velocityState.readIndex()].texture,
                divergenceProgram.uniform("u_velocity"));
        setCarrierTexel(divergenceProgram.uniform("u_texel"));
        drawFullscreen();
    }

    private void runPressurePass() {
        RenderTarget target = pressureTargets[pressureState.writeIndex()];
        beginFullscreenPass(pressureProgram, target);
        bindTexture(0, pressureTargets[pressureState.readIndex()].texture,
                pressureProgram.uniform("u_pressure"));
        bindTexture(1, divergenceTarget.texture,
                pressureProgram.uniform("u_divergence"));
        setCarrierTexel(pressureProgram.uniform("u_texel"));
        drawFullscreen();
        pressureState.swap();
    }

    private void runProjectionPass() {
        RenderTarget target = velocityTargets[velocityState.writeIndex()];
        beginFullscreenPass(projectionProgram, target);
        bindTexture(0, velocityTargets[velocityState.readIndex()].texture,
                projectionProgram.uniform("u_velocity"));
        bindTexture(1, pressureTargets[pressureState.readIndex()].texture,
                projectionProgram.uniform("u_pressure"));
        setCarrierTexel(projectionProgram.uniform("u_texel"));
        drawFullscreen();
        velocityState.swap();
    }

    private void runCarrierPass(double simulationTimeSeconds) {
        RenderTarget target = carrierTargets[carrierState.writeIndex()];
        beginFullscreenPass(carrierProgram, target);
        bindTexture(0, carrierTargets[carrierState.readIndex()].texture,
                carrierProgram.uniform("u_carrier"));
        bindTexture(1, velocityTargets[velocityState.readIndex()].texture,
                carrierProgram.uniform("u_velocity"));
        setCarrierTexel(carrierProgram.uniform("u_texel"));
        GLES30.glUniform1f(carrierProgram.uniform("u_delta_time"),
                profile.simulationStepSeconds());
        setMotion(carrierProgram.uniform("u_motion"), simulationTimeSeconds);
        GLES30.glUniform1f(carrierProgram.uniform("u_reset"), 0f);
        drawFullscreen();
        carrierState.swap();
    }

    private void runParticleUpdatePass(double simulationTimeSeconds) {
        GLES30.glUseProgram(particleUpdateProgram.id);
        bindTexture(0, velocityTargets[velocityState.readIndex()].texture,
                particleUpdateProgram.uniform("u_velocity"));
        setCarrierTexel(particleUpdateProgram.uniform("u_velocity_texel"));
        GLES30.glUniform1f(particleUpdateProgram.uniform("u_delta_time"),
                profile.simulationStepSeconds());
        setMotion(particleUpdateProgram.uniform("u_motion"), simulationTimeSeconds);

        GLES30.glBindVertexArray(particleVaos[particleState.readIndex()]);
        GLES30.glBindTransformFeedback(GLES30.GL_TRANSFORM_FEEDBACK,
                particleTransformFeedbacks[particleState.writeIndex()]);
        GLES30.glEnable(GLES30.GL_RASTERIZER_DISCARD);
        GLES30.glBeginTransformFeedback(GLES30.GL_POINTS);
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, profile.particleCount);
        GLES30.glEndTransformFeedback();
        GLES30.glDisable(GLES30.GL_RASTERIZER_DISCARD);
        GLES30.glBindTransformFeedback(GLES30.GL_TRANSFORM_FEEDBACK, 0);
        GLES30.glBindVertexArray(0);
        particleState.swap();
    }

    private void runParticleSplatPass() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, transientDepositTarget.framebuffer);
        GLES30.glViewport(0, 0, transientDepositTarget.width, transientDepositTarget.height);
        GLES30.glClearColor(0f, 0f, 0f, 0f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        GLES30.glUseProgram(particleSplatProgram.id);
        GLES30.glUniform2f(particleSplatProgram.uniform("u_deposit_resolution"),
                profile.depositWidth, profile.depositHeight);
        GLES30.glBindVertexArray(particleVaos[particleState.readIndex()]);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendEquation(GLES30.GL_FUNC_ADD);
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE);
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, profile.particleCount);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glBindVertexArray(0);
    }

    private void runDepositPass() {
        RenderTarget target = depositTargets[depositState.writeIndex()];
        beginFullscreenPass(depositProgram, target);
        bindTexture(0, transientDepositTarget.texture,
                depositProgram.uniform("u_transient"));
        bindTexture(1, depositTargets[depositState.readIndex()].texture,
                depositProgram.uniform("u_history"));
        bindTexture(2, velocityTargets[velocityState.readIndex()].texture,
                depositProgram.uniform("u_velocity"));
        setCarrierTexel(depositProgram.uniform("u_velocity_texel"));
        GLES30.glUniform1f(depositProgram.uniform("u_delta_time"),
                profile.simulationStepSeconds());
        drawFullscreen();
        depositState.swap();
    }

    private void renderSurface(double simulationTimeSeconds) {
        runScenePass();
        runParticleEmissivePass(simulationTimeSeconds);
        runDownsamplePass(emissiveTarget, bloomNearTargets[0]);
        runDownsamplePass(emissiveTarget, bloomFarTargets[0]);
        runBlurPass(bloomNearTargets[0], bloomNearTargets[1], 1f, 0f);
        runBlurPass(bloomNearTargets[1], bloomNearTargets[0], 0f, 1f);
        runBlurPass(bloomFarTargets[0], bloomFarTargets[1], 1f, 0f);
        runBlurPass(bloomFarTargets[1], bloomFarTargets[0], 0f, 1f);
        runFinalPass();
    }

    private void runScenePass() {
        beginFullscreenPass(sceneProgram, sceneTarget);
        bindTexture(0, carrierTargets[carrierState.readIndex()].texture,
                sceneProgram.uniform("u_carrier"));
        bindTexture(1, depositTargets[depositState.readIndex()].texture,
                sceneProgram.uniform("u_deposit"));
        GLES30.glUniform2f(sceneProgram.uniform("u_surface_resolution"),
                surfaceWidth, surfaceHeight);
        setCarrierTexel(sceneProgram.uniform("u_carrier_texel"));
        GLES30.glUniform2f(sceneProgram.uniform("u_deposit_texel"),
                1f / profile.depositWidth, 1f / profile.depositHeight);
        drawFullscreen();
    }

    private void runParticleEmissivePass(double simulationTimeSeconds) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, emissiveTarget.framebuffer);
        GLES30.glViewport(0, 0, emissiveTarget.width, emissiveTarget.height);
        GLES30.glClearColor(0f, 0f, 0f, 0f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        GLES30.glUseProgram(particleEmissiveProgram.id);
        GLES30.glUniform2f(particleEmissiveProgram.uniform("u_surface_resolution"),
                surfaceWidth, surfaceHeight);
        bindTexture(0, depositTargets[depositState.readIndex()].texture,
                particleEmissiveProgram.uniform("u_deposit"));
        bindTexture(1, carrierTargets[carrierState.readIndex()].texture,
                particleEmissiveProgram.uniform("u_carrier"));
        setCarrierTexel(particleEmissiveProgram.uniform("u_carrier_texel"));
        GLES30.glUniform1f(particleEmissiveProgram.uniform("u_time"),
                (float) simulationTimeSeconds);
        GLES30.glBindVertexArray(particleVaos[particleState.readIndex()]);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendEquation(GLES30.GL_FUNC_ADD);
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE);
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, profile.particleCount);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glBindVertexArray(0);
    }

    private void runDownsamplePass(RenderTarget source, RenderTarget target) {
        beginFullscreenPass(downsampleProgram, target);
        bindTexture(0, source.texture, downsampleProgram.uniform("u_source"));
        GLES30.glUniform2f(downsampleProgram.uniform("u_source_texel"),
                1f / source.width, 1f / source.height);
        GLES30.glUniform2f(downsampleProgram.uniform("u_source_scale"),
                (float) source.width / target.width, (float) source.height / target.height);
        drawFullscreen();
    }

    private void runBlurPass(
            RenderTarget source, RenderTarget target, float directionX, float directionY) {
        beginFullscreenPass(blurProgram, target);
        bindTexture(0, source.texture, blurProgram.uniform("u_source"));
        GLES30.glUniform2f(blurProgram.uniform("u_source_texel"),
                1f / source.width, 1f / source.height);
        GLES30.glUniform2f(blurProgram.uniform("u_direction"), directionX, directionY);
        drawFullscreen();
    }

    private void runFinalPass() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight);
        GLES30.glUseProgram(finalProgram.id);
        bindTexture(0, sceneTarget.texture, finalProgram.uniform("u_scene"));
        bindTexture(1, emissiveTarget.texture, finalProgram.uniform("u_emissive"));
        bindTexture(2, bloomNearTargets[0].texture, finalProgram.uniform("u_bloom_near"));
        bindTexture(3, bloomFarTargets[0].texture, finalProgram.uniform("u_bloom_far"));
        drawFullscreen();
        GLES30.glBindVertexArray(0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
    }

    private void beginFullscreenPass(ShaderProgram program, RenderTarget target) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.framebuffer);
        GLES30.glViewport(0, 0, target.width, target.height);
        GLES30.glUseProgram(program.id);
    }

    private void drawFullscreen() {
        GLES30.glBindVertexArray(fullscreenVao);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        GLES30.glBindVertexArray(0);
    }

    private void setCarrierTexel(int location) {
        GLES30.glUniform2f(location, 1f / profile.carrierWidth, 1f / profile.carrierHeight);
    }

    private static void setMotion(int location, double elapsedSeconds) {
        double primaryPhase = elapsedSeconds * Math.PI * 2.0 / 13.0;
        double secondaryPhase = elapsedSeconds * Math.PI * 2.0 / 5.0;
        GLES30.glUniform4f(location,
                (float) Math.sin(primaryPhase),
                (float) Math.cos(primaryPhase),
                (float) Math.sin(secondaryPhase),
                (float) Math.cos(secondaryPhase));
    }

    private static void bindTexture(int unit, int texture, int uniformLocation) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
        GLES30.glUniform1i(uniformLocation, unit);
    }

    private void fail(String message, Throwable error) {
        Log.w(TAG, message, error);
        deleteCurrentContextResources();
        if (unavailableSignaled) return;
        unavailableSignaled = true;
        if (unavailableCallback != null) unavailableCallback.run();
    }

    private void invalidateContextResources() {
        velocityProgram = null;
        divergenceProgram = null;
        pressureProgram = null;
        projectionProgram = null;
        carrierProgram = null;
        particleUpdateProgram = null;
        particleSplatProgram = null;
        depositProgram = null;
        sceneProgram = null;
        particleEmissiveProgram = null;
        downsampleProgram = null;
        blurProgram = null;
        finalProgram = null;
        fullscreenVao = 0;
        fullscreenBuffer = 0;
        particleBuffers[0] = 0;
        particleBuffers[1] = 0;
        particleVaos[0] = 0;
        particleVaos[1] = 0;
        particleTransformFeedbacks[0] = 0;
        particleTransformFeedbacks[1] = 0;
        invalidateTargets(velocityTargets);
        invalidateTargets(pressureTargets);
        divergenceTarget.invalidate();
        invalidateTargets(carrierTargets);
        transientDepositTarget.invalidate();
        invalidateTargets(depositTargets);
        sceneTarget.invalidate();
        emissiveTarget.invalidate();
        invalidateTargets(bloomNearTargets);
        invalidateTargets(bloomFarTargets);
        resourcesReady = false;
        surfaceTargetsReady = false;
        resetLogicalState();
    }

    private void deleteCurrentContextResources() {
        deleteProgram(velocityProgram);
        deleteProgram(divergenceProgram);
        deleteProgram(pressureProgram);
        deleteProgram(projectionProgram);
        deleteProgram(carrierProgram);
        deleteProgram(particleUpdateProgram);
        deleteProgram(particleSplatProgram);
        deleteProgram(depositProgram);
        deleteProgram(sceneProgram);
        deleteProgram(particleEmissiveProgram);
        deleteProgram(downsampleProgram);
        deleteProgram(blurProgram);
        deleteProgram(finalProgram);
        deleteTargets(velocityTargets);
        deleteTargets(pressureTargets);
        deleteTarget(divergenceTarget);
        deleteTargets(carrierTargets);
        deleteTarget(transientDepositTarget);
        deleteTargets(depositTargets);
        deleteSurfaceTargets();
        GLES30.glDeleteBuffers(2, particleBuffers, 0);
        GLES30.glDeleteVertexArrays(2, particleVaos, 0);
        GLES30.glDeleteTransformFeedbacks(2, particleTransformFeedbacks, 0);
        if (fullscreenBuffer != 0) {
            GLES30.glDeleteBuffers(1, new int[]{fullscreenBuffer}, 0);
        }
        if (fullscreenVao != 0) {
            GLES30.glDeleteVertexArrays(1, new int[]{fullscreenVao}, 0);
        }
        invalidateContextResources();
    }

    private void deleteSurfaceTargets() {
        deleteTarget(sceneTarget);
        deleteTarget(emissiveTarget);
        deleteTargets(bloomNearTargets);
        deleteTargets(bloomFarTargets);
        surfaceTargetsReady = false;
    }

    private static void deleteProgram(ShaderProgram program) {
        if (program != null && program.id != 0) GLES30.glDeleteProgram(program.id);
    }

    private static void deleteTargets(RenderTarget[] targets) {
        for (RenderTarget target : targets) deleteTarget(target);
    }

    private static void deleteTarget(RenderTarget target) {
        if (target.framebuffer != 0) {
            GLES30.glDeleteFramebuffers(1, new int[]{target.framebuffer}, 0);
        }
        if (target.texture != 0) {
            GLES30.glDeleteTextures(1, new int[]{target.texture}, 0);
        }
        target.invalidate();
    }

    private static void invalidateTargets(RenderTarget[] targets) {
        for (RenderTarget target : targets) target.invalidate();
    }

    private void resetLogicalState() {
        velocityState.reset();
        pressureState.reset();
        carrierState.reset();
        depositState.reset();
        particleState.reset();
    }

    private static void setFallbackClearColor() {
        GLES30.glClearColor(FALLBACK_RED, FALLBACK_GREEN, FALLBACK_BLUE, 1f);
    }

    private static void checkGlError(String operation) {
        int error = GLES30.glGetError();
        if (error != GLES30.GL_NO_ERROR) {
            throw new IllegalStateException(
                    operation + " failed with GL error 0x" + Integer.toHexString(error));
        }
    }

    private static void drainGlErrors() {
        while (GLES30.glGetError() != GLES30.GL_NO_ERROR) {
            // Target probes own subsequent error checks; stale errors cannot describe this target.
        }
    }

    private String readRawResource(int resourceId) throws IOException {
        try (InputStream input = context.getResources().openRawResource(resourceId);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static final class ShaderProgram {
        final int id;
        private final Map<String, Integer> uniforms = new HashMap<>();

        ShaderProgram(int id, String[] names) {
            this.id = id;
            for (String name : names) {
                int location = GLES30.glGetUniformLocation(id, name);
                if (location < 0) {
                    GLES30.glDeleteProgram(id);
                    throw new IllegalStateException("Missing shader uniform: " + name);
                }
                uniforms.put(name, location);
            }
        }

        int uniform(String name) {
            Integer location = uniforms.get(name);
            if (location == null) throw new IllegalArgumentException("Unknown uniform: " + name);
            return location;
        }
    }

    private static final class RenderTarget {
        int texture;
        int framebuffer;
        int width;
        int height;

        static RenderTarget[] createPair() {
            return new RenderTarget[]{new RenderTarget(), new RenderTarget()};
        }

        void invalidate() {
            texture = 0;
            framebuffer = 0;
            width = 0;
            height = 0;
        }
    }
}
