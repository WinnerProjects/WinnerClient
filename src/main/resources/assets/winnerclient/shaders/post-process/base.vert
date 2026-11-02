#version 330 core

layout (location = 0) in vec3 pos;

layout (std140) uniform PostData {
    vec2 u_Size;
    float u_Time;
};

out vec2 v_TexCoord;
out vec2 v_OneTexel;

void main() {
    gl_Position = vec4(pos, 1.0);
    v_TexCoord = (pos.xy + 1.0) / 2.0;
    v_OneTexel = 1.0 / u_Size;
}
