package com.winnerclient.fabric.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.resource.ResourceManager;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Self-contained GPU implementation of Meteor's StorageESP outline pass.
 *
 * <p>Pass 1 renders colored storage geometry into an off-screen RGBA texture.
 * Pass 2 runs the outline.frag kernel over that texture and composites the
 * result into Minecraft's main framebuffer. No Meteor classes are referenced.</p>
 */
public final class StorageShaderPipeline {
    private static final String MOD_ID = "winnerclient";
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private static final Identifier WORLD_PIPELINE_ID = Identifier.of(MOD_ID, "pipeline/storage_world");
    private static final Identifier POST_PIPELINE_ID = Identifier.of(MOD_ID, "pipeline/storage_outline");
    private static final Identifier WORLD_VERTEX = Identifier.of(MOD_ID, "shaders/pos_color.vert");
    private static final Identifier WORLD_FRAGMENT = Identifier.of(MOD_ID, "shaders/pos_color.frag");
    private static final Identifier POST_VERTEX = Identifier.of(MOD_ID, "shaders/post-process/base.vert");
    private static final Identifier POST_FRAGMENT = Identifier.of(MOD_ID, "shaders/post-process/outline.frag");

    private static final RenderPipeline WORLD_PIPELINE = RenderPipeline.builder()
        .withLocation(WORLD_PIPELINE_ID)
        .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(WORLD_VERTEX)
        .withFragmentShader(WORLD_FRAGMENT)
        .withUniform("MeshData", UniformType.UNIFORM_BUFFER)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build();

    private static final RenderPipeline POST_PIPELINE = RenderPipeline.builder()
        .withLocation(POST_PIPELINE_ID)
        .withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(POST_VERTEX)
        .withFragmentShader(POST_FRAGMENT)
        .withSampler("u_Texture")
        .withUniform("PostData", UniformType.UNIFORM_BUFFER)
        .withUniform("OutlineData", UniformType.UNIFORM_BUFFER)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build();

    private static final int MESH_UNIFORM_SIZE = new Std140SizeCalculator()
        .putMat4f().putMat4f().get();
    private static final int POST_UNIFORM_SIZE = new Std140SizeCalculator()
        .putVec2().putFloat().get();
    private static final int OUTLINE_UNIFORM_SIZE = new Std140SizeCalculator()
        .putInt().putFloat().putInt().putFloat().get();

    private static final DynamicUniformStorage<MeshData> MESH_UNIFORMS =
        new DynamicUniformStorage<>("WinnerClient - Storage Mesh UBO", MESH_UNIFORM_SIZE, 16);
    private static final DynamicUniformStorage<PostData> POST_UNIFORMS =
        new DynamicUniformStorage<>("WinnerClient - Storage Post UBO", POST_UNIFORM_SIZE, 16);
    private static final DynamicUniformStorage<OutlineData> OUTLINE_UNIFORMS =
        new DynamicUniformStorage<>("WinnerClient - Storage Outline UBO", OUTLINE_UNIFORM_SIZE, 16);

    private static Framebuffer storageFramebuffer;
    private static GpuBuffer fullscreenVertices;
    private static GpuBuffer fullscreenIndices;
    private static boolean disabled;

    private StorageShaderPipeline() {}

    /** Returns false only after a GPU setup failure; caller can fall back to Box mode. */
    public static boolean isAvailable() {
        return !disabled;
    }

    /**
     * Precompiles both custom pipelines after ShaderLoader has loaded resources.
     * This is the same loader integration used by Meteor: custom shader
     * identifiers are resolved through ResourceManager as
     * assets/winnerclient/<path>. Without this hook a first-use lazy compile can
     * fail because vanilla's shader definition set does not contain mod sources.
     */
    public static void precompile() {
        if (disabled) return;
        GpuDevice device = RenderSystem.getDevice();
        ResourceManager resources = MC.getResourceManager();
        precompile(device, resources, WORLD_PIPELINE);
        precompile(device, resources, POST_PIPELINE);
    }

    private static void precompile(GpuDevice device, ResourceManager resources, RenderPipeline pipeline) {
        device.precompilePipeline(pipeline, (identifier, shaderType) -> {
            var resource = resources.getResource(identifier).orElseThrow(() ->
                new IllegalStateException("Missing WinnerClient shader resource " + identifier));
            try (var stream = resource.getInputStream()) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read WinnerClient shader resource " + identifier, e);
            }
        });
    }

    /** Releases the pass and its framebuffer at client shutdown. */
    public static void close() {
        if (storageFramebuffer != null) storageFramebuffer.delete();
        storageFramebuffer = null;
        if (fullscreenVertices != null) fullscreenVertices.close();
        if (fullscreenIndices != null) fullscreenIndices.close();
        fullscreenVertices = null;
        fullscreenIndices = null;
        MESH_UNIFORMS.close();
        POST_UNIFORMS.close();
        OUTLINE_UNIFORMS.close();
    }

    public static void onResize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        ensureFramebuffer(width, height);
    }

    /**
     * Convenience API for StorageESP: converts world-space boxes to a
     * POSITION_COLOR mesh and explicitly subtracts the active camera position.
     */
    public static void renderBoxes(
        java.util.List<BoxColor> boxes, Matrix4f projection, Matrix4f modelView,
        Vec3d cameraPos, int outlineWidth, float fillOpacity, int shapeMode, float glowMultiplier
    ) {
        if (boxes == null || boxes.isEmpty()) return;
        ByteBuffer vertices = BufferUtils.createByteBuffer(boxes.size() * 8 * 16);
        ByteBuffer indices = BufferUtils.createByteBuffer(boxes.size() * 36 * Integer.BYTES);
        int vertex = 0;
        for (BoxColor item : boxes) {
            Box b = item.box();
            int argb = item.argb();
            int a = (argb >>> 24) & 0xFF;
            int r = (argb >>> 16) & 0xFF;
            int g = (argb >>> 8) & 0xFF;
            int bl = argb & 0xFF;
            double[][] p = {
                { b.minX, b.minY, b.minZ }, { b.maxX, b.minY, b.minZ },
                { b.maxX, b.maxY, b.minZ }, { b.minX, b.maxY, b.minZ },
                { b.minX, b.minY, b.maxZ }, { b.maxX, b.minY, b.maxZ },
                { b.maxX, b.maxY, b.maxZ }, { b.minX, b.maxY, b.maxZ }
            };
            for (double[] q : p) {
                // World render vertices must be camera-relative. The normal
                // world path applies this translation through the pose stack;
                // this direct GPU path does it explicitly.
                vertices.putFloat((float) (q[0] - cameraPos.x))
                    .putFloat((float) (q[1] - cameraPos.y))
                    .putFloat((float) (q[2] - cameraPos.z));
                vertices.put((byte) r).put((byte) g).put((byte) bl).put((byte) a);
            }
            int[] faces = {
                0, 1, 2, 2, 3, 0, 4, 6, 5, 6, 4, 7,
                0, 4, 5, 5, 1, 0, 3, 2, 6, 6, 7, 3,
                0, 3, 7, 7, 4, 0, 1, 5, 6, 6, 2, 1
            };
            for (int i : faces) indices.putInt(vertex + i);
            vertex += 8;
        }
        vertices.flip();
        indices.flip();
        render(vertices, indices, vertex, indices.remaining() / Integer.BYTES, projection, modelView,
            outlineWidth, fillOpacity, shapeMode, glowMultiplier);
    }

    public record BoxColor(Box box, int argb) {}

    /**
     * Draws a colored triangle mesh into the mask framebuffer and composites a
     * real configurable outline into Minecraft's current framebuffer.
     *
     * @param vertices POSITION_COLOR vertex data (16-byte vertices)
     * @param indices triangle indices
     * @param vertexCount number of vertices
     * @param indexCount number of indices
     * @param projection current frame projection matrix from GameRenderer
     * @param modelView current world model-view matrix
     * @param outlineWidth pixel radius used by outline.frag
     * @param fillOpacity 0..1 fill opacity
     * @param shapeMode 0=lines, 1=sides, 2=both
     * @param glowMultiplier outline glow multiplier
     */
    public static void render(
        ByteBuffer vertices, ByteBuffer indices, int vertexCount, int indexCount,
        Matrix4f projection, Matrix4f modelView,
        int outlineWidth, float fillOpacity, int shapeMode, float glowMultiplier
    ) {
        if (disabled || vertexCount <= 0 || indexCount <= 0) return;
        int width = MC.getWindow().getFramebufferWidth();
        int height = MC.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) return;

        try {
            ensureFramebuffer(width, height);
            ensureFullscreenBuffers();

            GpuBuffer vertexBuffer = VertexFormats.POSITION_COLOR.uploadImmediateVertexBuffer(vertices);
            GpuBuffer indexBuffer = VertexFormats.POSITION_COLOR.uploadImmediateIndexBuffer(indices);
            try {
                GpuBufferSlice meshData = MESH_UNIFORMS.write(new MeshData(projection, modelView));
                RenderPass maskPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "WinnerClient StorageESP Mask", storageFramebuffer.getColorAttachmentView(),
                    OptionalInt.of(0), storageFramebuffer.getDepthAttachmentView(), OptionalDouble.empty()
                );
                maskPass.setPipeline(WORLD_PIPELINE);
                maskPass.setUniform("MeshData", meshData);
                maskPass.setVertexBuffer(0, vertexBuffer);
                maskPass.setIndexBuffer(indexBuffer, VertexFormat.IndexType.INT);
                maskPass.drawIndexed(0, 0, indexCount, 1);
                maskPass.close();

                GpuBufferSlice postData = POST_UNIFORMS.write(new PostData(width, height, (float) GLFW_TIME.get()));
                GpuBufferSlice outlineData = OUTLINE_UNIFORMS.write(new OutlineData(
                    MathHelper.clamp(outlineWidth, 1, 10),
                    MathHelper.clamp(fillOpacity, 0.0f, 1.0f),
                    MathHelper.clamp(shapeMode, 0, 2),
                    Math.max(0.0f, glowMultiplier)
                ));
                RenderPass postPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "WinnerClient StorageESP Outline", MC.getFramebuffer().getColorAttachmentView(),
                    OptionalInt.empty()
                );
                postPass.setPipeline(POST_PIPELINE);
                postPass.setUniform("PostData", postData);
                postPass.setUniform("OutlineData", outlineData);
                postPass.bindTexture("u_Texture", storageFramebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
                postPass.setVertexBuffer(0, fullscreenVertices);
                postPass.setIndexBuffer(fullscreenIndices, VertexFormat.IndexType.INT);
                postPass.drawIndexed(0, 0, 6, 1);
                postPass.close();
            } finally {
                vertexBuffer.close();
                indexBuffer.close();
            }
        } catch (Throwable error) {
            disabled = true;
            System.err.println("WinnerClient StorageESP shader pipeline disabled after GPU error: " + error);
        }
    }

    private static void ensureFramebuffer(int width, int height) {
        if (storageFramebuffer == null) {
            storageFramebuffer = new SimpleFramebuffer("WinnerClient StorageESP", width, height, true);
        } else if (storageFramebuffer.textureWidth != width || storageFramebuffer.textureHeight != height) {
            storageFramebuffer.resize(width, height);
        }
    }

    private static void ensureFullscreenBuffers() {
        if (fullscreenVertices != null) return;
        ByteBuffer vertices = BufferUtils.createByteBuffer(4 * 12);
        FloatBuffer f = vertices.asFloatBuffer();
        f.put(-1).put(-1).put(0);
        f.put(-1).put(1).put(0);
        f.put(1).put(1).put(0);
        f.put(1).put(-1).put(0);
        vertices.limit(4 * 12);
        ByteBuffer indices = BufferUtils.createByteBuffer(6 * Integer.BYTES);
        indices.putInt(0).putInt(1).putInt(2).putInt(2).putInt(3).putInt(0).flip();
        fullscreenVertices = VertexFormats.POSITION.uploadImmediateVertexBuffer(vertices);
        fullscreenIndices = VertexFormats.POSITION.uploadImmediateIndexBuffer(indices);
    }

    private record MeshData(Matrix4f projection, Matrix4f modelView) implements DynamicUniformStorage.Uploadable {
        @Override public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putMat4f(projection).putMat4f(modelView);
        }
    }

    private record PostData(float width, float height, float time) implements DynamicUniformStorage.Uploadable {
        @Override public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putVec2(width, height).putFloat(time);
        }
    }

    private record OutlineData(int width, float fillOpacity, int shapeMode, float glowMultiplier) implements DynamicUniformStorage.Uploadable {
        @Override public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putInt(width).putFloat(fillOpacity).putInt(shapeMode).putFloat(glowMultiplier);
        }
    }

    /** GLFW time is isolated to avoid a hard dependency in the public module API. */
    private static final class GLFW_TIME {
        static double get() { return org.lwjgl.glfw.GLFW.glfwGetTime(); }
    }
}
