#version 130

const float dither_matrix[16] = float[](
    0.0, 0.5, 0.125, 0.625,
    0.75, 0.25, 0.875, 0.375,
    0.0625, 0.5625, 0.03125, 0.53125,
    0.8125, 0.4375, 0.78125, 0.40625
);


flat in float pass_opacity;
in vec3 pass_color;

out vec3 out_color;

void main() {
    int x = int(gl_FragCoord.x) % 4;
    int y = int(gl_FragCoord.y) % 4;
    int index = x + y * 4;

    if(pass_opacity <= dither_matrix[index]) {
        // TODO: Maybe there is a way to achieve the same without using discard, to enable the early z test
        discard;
    }

    out_color = pass_color;
}