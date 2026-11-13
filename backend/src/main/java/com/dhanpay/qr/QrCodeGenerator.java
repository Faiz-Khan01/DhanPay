package com.dhanpay.qr;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Self-contained, dependency-free QR Code generator (ISO/IEC 18004) supporting
 * Byte mode encoding (UTF-8), Error Correction Level M (15% recovery),
 * and versions 1 to 7 (up to 130 bytes), rendering to standalone SVG.
 */
public final class QrCodeGenerator {

    private static final int[] EXP = new int[512];
    private static final int[] LOG = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            EXP[i + 255] = x;
            LOG[x] = i;
            x = (x << 1) ^ ((x >= 128) ? 0x11D : 0);
        }
    }

    private static int gfMul(int a, int b) {
        if (a == 0 || b == 0) return 0;
        return EXP[LOG[a] + LOG[b]];
    }

    private static final int[][] VERSION_PARAMS_M = {
            {1, 21, 26, 10, 1, 16, 0, 0},
            {2, 25, 44, 16, 1, 28, 0, 0},
            {3, 29, 70, 26, 1, 44, 0, 0},
            {4, 33, 100, 18, 2, 32, 0, 0},
            {5, 37, 134, 24, 2, 43, 0, 0},
            {6, 41, 172, 16, 4, 27, 0, 0},
            {7, 45, 196, 18, 4, 31, 0, 0},
    };

    private static final int[][] ALIGNMENT_COORDS = {
            {},
            {6, 18},
            {6, 22},
            {6, 26},
            {6, 30},
            {6, 34},
            {6, 22, 38},
    };

    private QrCodeGenerator() {
    }

    public static boolean[][] encode(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int[] params = selectVersion(data.length);
        int version = params[0];
        int size = params[1];
        int totalDataCodewords = params[4] * params[5] + params[6] * params[7];

        byte[] bitStream = buildBitStream(data, totalDataCodewords);
        byte[] allCodewords = interleaveCodewords(bitStream, params);

        boolean[][] modules = new boolean[size][size];
        boolean[][] isFunction = new boolean[size][size];

        applyFunctionPatterns(version, size, modules, isFunction);
        placeDataCodewords(allCodewords, size, modules, isFunction);
        applyMask(size, modules, isFunction);
        applyFormatInfo(size, modules);

        return modules;
    }

    public static String toSvg(boolean[][] modules, int quietZone, String darkColor, String lightColor) {
        int size = modules.length;
        int totalSize = size + quietZone * 2;
        StringBuilder path = new StringBuilder();

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (modules[r][c]) {
                    path.append("M").append(c + quietZone).append(",").append(r + quietZone)
                            .append("h1v1h-1z");
                }
            }
        }

        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + totalSize + " " + totalSize + "\" shape-rendering=\"crispEdges\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"" + lightColor + "\"/>"
                + "<path fill=\"" + darkColor + "\" d=\"" + path + "\"/>"
                + "</svg>";
    }

    private static int[] selectVersion(int dataLen) {
        for (int[] p : VERSION_PARAMS_M) {
            int totalDataCodewords = p[4] * p[5] + p[6] * p[7];
            if (dataLen + 2 <= totalDataCodewords) {
                return p;
            }
        }
        throw new IllegalArgumentException("Payload too large for QR generator: " + dataLen + " bytes");
    }

    private static byte[] buildBitStream(byte[] data, int targetCodewords) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int bitBuf = 0;
        int bitCount = 0;

        bitBuf = (bitBuf << 4) | 0b0100;
        bitCount += 4;

        bitBuf = (bitBuf << 8) | data.length;
        bitCount += 8;

        while (bitCount >= 8) {
            bitCount -= 8;
            baos.write((bitBuf >>> bitCount) & 0xFF);
        }

        for (byte b : data) {
            bitBuf = (bitBuf << 8) | (b & 0xFF);
            bitCount += 8;
            while (bitCount >= 8) {
                bitCount -= 8;
                baos.write((bitBuf >>> bitCount) & 0xFF);
            }
        }

        int remainingBits = (targetCodewords * 8) - (baos.size() * 8 + bitCount);
        int terminatorBits = Math.min(4, Math.max(0, remainingBits));
        bitBuf = (bitBuf << terminatorBits);
        bitCount += terminatorBits;

        if (bitCount > 0) {
            int pad = 8 - bitCount;
            bitBuf <<= pad;
            baos.write(bitBuf & 0xFF);
            bitCount = 0;
        }

        byte[] padBytes = {(byte) 0xEC, (byte) 0x11};
        int padIdx = 0;
        while (baos.size() < targetCodewords) {
            baos.write(padBytes[padIdx % 2]);
            padIdx++;
        }

        return baos.toByteArray();
    }

    private static byte[] interleaveCodewords(byte[] dataCodewords, int[] params) {
        int ecCodewordsPerBlock = params[3];
        int numBlocksG1 = params[4];
        int dataG1 = params[5];
        int numBlocksG2 = params[6];
        int dataG2 = params[7];
        int totalBlocks = numBlocksG1 + numBlocksG2;

        byte[][] dataBlocks = new byte[totalBlocks][];
        byte[][] ecBlocks = new byte[totalBlocks][];

        int offset = 0;
        for (int b = 0; b < totalBlocks; b++) {
            int blockDataLen = (b < numBlocksG1) ? dataG1 : dataG2;
            dataBlocks[b] = Arrays.copyOfRange(dataCodewords, offset, offset + blockDataLen);
            offset += blockDataLen;
            ecBlocks[b] = computeReedSolomon(dataBlocks[b], ecCodewordsPerBlock);
        }

        byte[] result = new byte[params[2]];
        int resIdx = 0;

        int maxDataLen = Math.max(dataG1, dataG2);
        for (int i = 0; i < maxDataLen; i++) {
            for (int b = 0; b < totalBlocks; b++) {
                if (i < dataBlocks[b].length) {
                    result[resIdx++] = dataBlocks[b][i];
                }
            }
        }

        for (int i = 0; i < ecCodewordsPerBlock; i++) {
            for (int b = 0; b < totalBlocks; b++) {
                result[resIdx++] = ecBlocks[b][i];
            }
        }

        return result;
    }

    private static byte[] computeReedSolomon(byte[] data, int ecCount) {
        int[] generator = new int[]{1};
        for (int i = 0; i < ecCount; i++) {
            int root = EXP[i];
            int[] next = new int[generator.length + 1];
            for (int j = 0; j < generator.length; j++) {
                next[j] ^= generator[j];
                next[j + 1] ^= gfMul(generator[j], root);
            }
            generator = next;
        }

        int[] remainder = new int[ecCount];
        for (byte b : data) {
            int factor = (b & 0xFF) ^ remainder[0];
            System.arraycopy(remainder, 1, remainder, 0, ecCount - 1);
            remainder[ecCount - 1] = 0;
            for (int i = 0; i < ecCount; i++) {
                remainder[i] ^= gfMul(generator[i + 1], factor);
            }
        }

        byte[] result = new byte[ecCount];
        for (int i = 0; i < ecCount; i++) {
            result[i] = (byte) remainder[i];
        }
        return result;
    }

    private static void applyFunctionPatterns(int version, int size, boolean[][] modules, boolean[][] isFunction) {
        drawFinder(0, 0, modules, isFunction);
        drawFinder(size - 7, 0, modules, isFunction);
        drawFinder(0, size - 7, modules, isFunction);

        drawSeparators(size, modules, isFunction);

        int[] coords = ALIGNMENT_COORDS[version - 1];
        for (int r : coords) {
            for (int c : coords) {
                if ((r <= 8 && c <= 8) || (r <= 8 && c >= size - 8) || (r >= size - 8 && c <= 8)) {
                    continue;
                }
                drawAlignment(r, c, modules, isFunction);
            }
        }

        for (int i = 8; i < size - 8; i++) {
            boolean color = (i % 2 == 0);
            modules[6][i] = color;
            isFunction[6][i] = true;
            modules[i][6] = color;
            isFunction[i][6] = true;
        }

        modules[4 * version + 9][8] = true;
        isFunction[4 * version + 9][8] = true;

        for (int i = 0; i <= 8; i++) {
            isFunction[8][i] = true;
            isFunction[i][8] = true;
        }
        for (int i = size - 8; i < size; i++) {
            isFunction[8][i] = true;
            isFunction[i][8] = true;
        }
    }

    private static void drawFinder(int r, int c, boolean[][] modules, boolean[][] isFunction) {
        for (int dr = 0; dr < 7; dr++) {
            for (int dc = 0; dc < 7; dc++) {
                boolean dark = (dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr >= 2 && dr <= 4 && dc >= 2 && dc <= 4));
                modules[r + dr][c + dc] = dark;
                isFunction[r + dr][c + dc] = true;
            }
        }
    }

    private static void drawSeparators(int size, boolean[][] modules, boolean[][] isFunction) {
        for (int i = 0; i < 8; i++) {
            if (i < size) {
                isFunction[7][i] = true;
                modules[7][i] = false;
                isFunction[i][7] = true;
                modules[i][7] = false;
            }
        }
        for (int i = 0; i < 8; i++) {
            int c = size - 8 + i;
            if (c >= 0 && c < size) {
                isFunction[7][c] = true;
                modules[7][c] = false;
                isFunction[i][size - 8] = true;
                modules[i][size - 8] = false;
            }
        }
        for (int i = 0; i < 8; i++) {
            int r = size - 8 + i;
            if (r >= 0 && r < size) {
                isFunction[r][7] = true;
                modules[r][7] = false;
                isFunction[size - 8][i] = true;
                modules[size - 8][i] = false;
            }
        }
    }

    private static void drawAlignment(int r, int c, boolean[][] modules, boolean[][] isFunction) {
        for (int dr = -2; dr <= 2; dr++) {
            for (int dc = -2; dc <= 2; dc++) {
                boolean dark = (Math.abs(dr) == 2 || Math.abs(dc) == 2 || (dr == 0 && dc == 0));
                modules[r + dr][c + dc] = dark;
                isFunction[r + dr][c + dc] = true;
            }
        }
    }

    private static void placeDataCodewords(byte[] allCodewords, int size, boolean[][] modules, boolean[][] isFunction) {
        int bitIdx = 0;
        int totalBits = allCodewords.length * 8;
        int right = size - 1;

        while (right > 0) {
            if (right == 6) right--;
            for (int vert = 0; vert < size; vert++) {
                for (int colOffset = 0; colOffset < 2; colOffset++) {
                    int col = right - colOffset;
                    boolean upward = ((right + 1) / 2) % 2 == 1;
                    int row = upward ? (size - 1 - vert) : vert;

                    if (!isFunction[row][col]) {
                        boolean bit = false;
                        if (bitIdx < totalBits) {
                            int byteVal = allCodewords[bitIdx / 8] & 0xFF;
                            int bitPos = 7 - (bitIdx % 8);
                            bit = ((byteVal >>> bitPos) & 1) == 1;
                            bitIdx++;
                        }
                        modules[row][col] = bit;
                    }
                }
            }
            right -= 2;
        }
    }

    private static void applyMask(int size, boolean[][] modules, boolean[][] isFunction) {
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (!isFunction[r][c] && (r + c) % 2 == 0) {
                    modules[r][c] = !modules[r][c];
                }
            }
        }
    }

    private static void applyFormatInfo(int size, boolean[][] modules) {
        int formatBits = 0x5412;
        int[] r1 = {0, 1, 2, 3, 4, 5, 7, 8, 8, 8, 8, 8, 8, 8, 8};
        int[] c1 = {8, 8, 8, 8, 8, 8, 8, 8, 7, 5, 4, 3, 2, 1, 0};
        for (int i = 0; i < 15; i++) {
            boolean bit = ((formatBits >>> i) & 1) == 1;
            modules[r1[i]][c1[i]] = bit;
        }

        for (int i = 0; i < 7; i++) {
            boolean bit = ((formatBits >>> i) & 1) == 1;
            modules[size - 1 - i][8] = bit;
        }
        for (int i = 7; i < 15; i++) {
            boolean bit = ((formatBits >>> i) & 1) == 1;
            modules[8][size - 15 + i] = bit;
        }
    }
}
