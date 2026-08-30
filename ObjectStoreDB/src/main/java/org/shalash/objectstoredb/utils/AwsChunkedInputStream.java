package org.shalash.objectstoredb.utils;

import java.io.IOException;
import java.io.InputStream;

public class AwsChunkedInputStream extends InputStream {

    private final InputStream in;

    private int remainingChunkBytes = 0;

    private boolean finished = false;

    public AwsChunkedInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public int read() throws IOException {

        if (finished) {
            return -1;
        }

        if (remainingChunkBytes == 0) {

            readNextChunkHeader();

            if (finished) {
                return -1;
            }
        }

        int b = in.read();

        if (b == -1) {
            return -1;
        }

        remainingChunkBytes--;

        if (remainingChunkBytes == 0) {

            in.read(); // \r
            in.read(); // \n
        }

        return b;
    }

    private void readNextChunkHeader() throws IOException {

        String header = readLine();

        /*
            مثال:
            10000;chunk-signature=abc123
         */

        String hexSize = header.split(";")[0];

        int size = Integer.parseInt(hexSize.trim(), 16);

        if (size == 0) {

            finished = true;

            readLine();

            return;
        }

        remainingChunkBytes = size;
    }

    private String readLine() throws IOException {

        StringBuilder sb = new StringBuilder();

        int c;

        while ((c = in.read()) != -1) {

            if (c == '\r') {

                int next = in.read();

                if (next == '\n') {
                    break;
                }
            }

            sb.append((char) c);
        }

        return sb.toString();
    }
}
