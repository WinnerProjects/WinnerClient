#version 330 core

layout (location = 0) in vec3 pos;
layout (location = 1) in vec4 color;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

out vec4 v_Color;

void main() {
    gl_Position = u_Proj * u_ModelView * vec4(pos, 1.0);
    v_Color = color;
}
