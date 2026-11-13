#version 300 es
precision mediump float;

uniform vec2 u_resolution;        // viewport size in pixels
uniform float u_pxPerMm;          // pixels per millimetre (screen density)
uniform float u_ruleSpacingMm;    // ruling spacing in millimetres
uniform float u_marginMm;         // left margin line position in millimetres

out vec4 fragColor;

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec2 xy = gl_FragCoord.xy;

    // Cream paper base with subtle fibre/grain noise (no texture assets).
    vec3 color = vec3(0.996, 0.976, 0.890);
    float n = hash(xy * 0.020);
    color += (n - 0.5) * 0.014;
    float m = hash(xy * 0.050 + vec2(17.3, -41.9));
    color += (m - 0.5) * 0.007;
    color = clamp(color, 0.0, 1.0);

    // Horizontal ruling lines spaced u_ruleSpacingMm millimetres apart.
    float ruleSpacing = u_ruleSpacingMm * u_pxPerMm;
    float y = mod(xy.y, ruleSpacing);
    float d = min(y, ruleSpacing - y);
    float rule = 1.0 - smoothstep(0.0, 1.0, d);
    rule = pow(rule, 1.4);

    // Left margin line.
    float marginX = u_marginMm * u_pxPerMm;
    float margin = 1.0 - smoothstep(0.0, 1.0, abs(xy.x - marginX));

    // Ruled paper look: blue-grey rulings, pinkish margin line.
    color = mix(color, vec3(0.62, 0.72, 0.86), rule * 0.6);
    color = mix(color, vec3(0.94, 0.55, 0.55), margin * 0.55);

    fragColor = vec4(color, 1.0);
}
