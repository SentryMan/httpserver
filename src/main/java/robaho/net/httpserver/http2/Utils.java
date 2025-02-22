package robaho.net.httpserver.http2;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Deque;
import java.util.List;

public class Utils {

	public static int convertToInt(byte[] bytes, int off) throws Exception {
		return convertToInt(bytes, off, 4);
	}

	public static int convertToInt(byte[] bytes, int off, int length) throws Exception {
		int result = 0;
		if (off + length <= bytes.length) {

			int counter = 0;
			for (int i = off + length-1 ; i >=off ; i--) {
				result |= (bytes[i] & 0x00ff) << (8 * counter);
				counter++;
			}

		} else {
			throw new Exception(String.format("cannot read %s bytes from offset, goes beyond array boundaries", length));
		}
		return result;

	}

	public static long convertToLong(byte[] bytes, int off, int length) throws Exception {
		long result = 0;
		if (off + length <= bytes.length) {
			int counter = 0;
			for (int i = off + length-1 ; i >=off ; i--) {
				result |= Long.valueOf(bytes[i] & 0xff) << (8 * counter);
				counter++;
			}
		} else {
			throw new Exception(String.format("cannot read %s bytes from offset, goes beyond array boundries", length));
		}
		return result;

	}

	public static void convertToBinary(byte[] buffer, int pos, int input) {
		convertToBinary(buffer, pos, input, 4);
	}

	public static void convertToBinary(byte[] buffer, int pos, int input, int length) {
		for (int i = 0; i < length; i++) {
			buffer[i+pos] = (byte) ((input >> (8 * (length-1-i))) & 255);
		}
	}

    public static void writeBinary(OutputStream os,int input) throws IOException {
        writeBinary(os, input, 4);
    }

    public static void writeBinary(OutputStream os,int input,int length) throws IOException {
		for (int i = length-1; i>=0; i--) {
			os.write((byte) ((input >> (8 * i)) & 0xFF));
		}
    }
    private static final byte[] EMPTY = new byte[0];

    public static byte[] combineByteArrays(byte[]... blocks) {
        if(blocks.length==0) return EMPTY;
        if(blocks.length==1) return blocks[0];

        int totalLength = 0;
        for (byte[] block : blocks) {
            totalLength += block.length;
        }

        byte[] combined = new byte[totalLength];
        int offset = 0;
        for (byte[] block : blocks) {
            System.arraycopy(block, 0, combined, offset, block.length);
            offset += block.length;
        }

        return combined;
    }

    public static byte[] combineByteArrays(List<byte[]> blocks) {
        if(blocks.isEmpty()) return EMPTY;
        if(blocks.size()==1) return blocks.get(0);

        int totalLength = 0;
        for (byte[] block : blocks) {
            totalLength += block.length;
        }

        byte[] combined = new byte[totalLength];
        int offset = 0;
        for (byte[] block : blocks) {
            System.arraycopy(block, 0, combined, offset, block.length);
            offset += block.length;
        }

        return combined;
    }
    public static byte[] combineByteArrays(List<byte[]> array1,List<byte[]> array2) {
        int totalLength = 0;
        for (byte[] block : array1) {
            totalLength += block.length;
        }
        for (byte[] block : array2) {
            totalLength += block.length;
        }
        if(totalLength==0) return EMPTY;

        byte[] combined = new byte[totalLength];

        int offset = 0;
        for (byte[] block : array1) {
            System.arraycopy(block, 0, combined, offset, block.length);
            offset += block.length;
        }
        for (byte[] block : array2) {
            System.arraycopy(block, 0, combined, offset, block.length);
            offset += block.length;
        }

        return combined;
    }
}