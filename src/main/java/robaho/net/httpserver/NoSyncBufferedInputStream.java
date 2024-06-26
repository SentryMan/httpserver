package robaho.net.httpserver;

import java.util.Arrays;
import java.util.Objects;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * simple buffered input stream with no synchronization. only supports mark readlimit up to the buffer size
 */
public class NoSyncBufferedInputStream extends FilterInputStream {

    private static final int DEFAULT_BUFFER_SIZE = 8192;

    protected byte[] buf;

    /**
     * The index one greater than the index of the last valid byte in
     * the buffer.
     * This value is always
     * in the range {@code 0} through {@code buf.length};
     * elements {@code buf[0]} through {@code buf[count-1]}
     * contain buffered input data obtained
     * from the underlying  input stream.
     */
    protected int count;

    /**
     * The current position in the buffer. This is the index of the next
     * byte to be read from the {@code buf} array.
     * <p>
     * This value is always in the range {@code 0}
     * through {@code count}. If it is less
     * than {@code count}, then  {@code buf[pos]}
     * is the next byte to be supplied as input;
     * if it is equal to {@code count}, then
     * the  next {@code read} or {@code skip}
     * operation will require more bytes to be
     * read from the contained  input stream.
     *
     * @see     java.io.BufferedInputStream#buf
     */
    protected int pos;

    /**
     * The value of the {@code pos} field at the time the last
     * {@code mark} method was called.
     * <p>
     * This value is always
     * in the range {@code -1} through {@code pos}.
     * If there is no marked position in  the input
     * stream, this field is {@code -1}. If
     * there is a marked position in the input
     * stream,  then {@code buf[markpos]}
     * is the first byte to be supplied as input
     * after a {@code reset} operation. If
     * {@code markpos} is not {@code -1},
     * then all bytes from positions {@code buf[markpos]}
     * through  {@code buf[pos-1]} must remain
     * in the buffer array (though they may be
     * moved to  another place in the buffer array,
     * with suitable adjustments to the values
     * of {@code count},  {@code pos},
     * and {@code markpos}); they may not
     * be discarded unless and until the difference
     * between {@code pos} and {@code markpos}
     * exceeds {@code marklimit}.
     *
     * @see     java.io.BufferedInputStream#mark(int)
     * @see     java.io.BufferedInputStream#pos
     */
    protected int markpos = -1;

    /**
     * The maximum read ahead allowed after a call to the
     * {@code mark} method before subsequent calls to the
     * {@code reset} method fail.
     * Whenever the difference between {@code pos}
     * and {@code markpos} exceeds {@code marklimit},
     * then the  mark may be dropped by setting
     * {@code markpos} to {@code -1}.
     *
     * @see     java.io.BufferedInputStream#mark(int)
     * @see     java.io.BufferedInputStream#reset()
     */
    protected int marklimit;

    /**
     * Check to make sure that underlying input stream has not been
     * nulled out due to close; if not return it;
     */
    private InputStream getInIfOpen() throws IOException {
        InputStream input = in;
        if (input == null)
            throw new IOException("Stream closed");
        return input;
    }

    /**
     * Throws IOException if the stream is closed (buf is null).
     */
    private void ensureOpen() throws IOException {
        if (buf == null) {
            throw new IOException("Stream closed");
        }
    }

    /**
     * Throws IOException if the stream is closed (buf is null).
     */
    private byte[] getBufIfOpen() throws IOException {
        if (buf == null) {
            throw new IOException("Stream closed");
        }
        return buf;
    }


    /**
     * Creates a {@code BufferedInputStream}
     * and saves its  argument, the input stream
     * {@code in}, for later use. An internal
     * buffer array is created and  stored in {@code buf}.
     *
     * @param   in   the underlying input stream.
     */
    public NoSyncBufferedInputStream(InputStream in) {
        this(in, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Creates a {@code BufferedInputStream}
     * with the specified buffer size,
     * and saves its  argument, the input stream
     * {@code in}, for later use.  An internal
     * buffer array of length  {@code size}
     * is created and stored in {@code buf}.
     *
     * @param   in     the underlying input stream.
     * @param   size   the buffer size.
     * @throws  IllegalArgumentException if {@code size <= 0}.
     */
    public NoSyncBufferedInputStream(InputStream in, int size) {
        super(in);
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        buf = new byte[size];
    }

    /**
     * Fills the buffer with more data, taking into account
     * shuffling and other tricks for dealing with marks.
     * Assumes that it is being called by a locked method.
     * This method also assumes that all data has already been read in,
     * hence pos > count.
     */
    private void fill() throws IOException {
        byte[] buffer = getBufIfOpen();
        if (markpos == -1)
            pos = 0;            /* no mark: throw away the buffer */
        else if (pos >= buffer.length) { /* no room left in buffer */
            if (markpos > 0) {  /* can throw away early part of the buffer */
                int sz = pos - markpos;
                System.arraycopy(buffer, markpos, buffer, 0, sz);
                pos = sz;
                markpos = 0;
            } else if (buffer.length >= marklimit) {
                markpos = -1;   /* buffer got too big, invalidate mark */
                pos = 0;        /* drop buffer contents */
            }
        }
        count = pos;
        int n = getInIfOpen().read(buffer, pos, buffer.length - pos);
        if (n > 0)
            count = n + pos;
    }

    /**
     * See
     * the general contract of the {@code read}
     * method of {@code InputStream}.
     *
     * @return     the next byte of data, or {@code -1} if the end of the
     *             stream is reached.
     * @throws     IOException  if this input stream has been closed by
     *                          invoking its {@link #close()} method,
     *                          or an I/O error occurs.
     * @see        java.io.FilterInputStream#in
     */
    public int read() throws IOException {
        if (pos >= count) {
            fill();
            if (pos >= count)
                return -1;
        }
        return getBufIfOpen()[pos++] & 0xff;
    }

    /**
     * Read bytes into a portion of an array, reading from the underlying
     * stream at most once if necessary.
     */
    private int read1(byte[] b, int off, int len) throws IOException {
        int avail = count - pos;
        if (avail <= 0) {
            /* If the requested length is at least as large as the buffer, and
               if there is no mark/reset activity, do not bother to copy the
               bytes into the local buffer.  In this way buffered streams will
               cascade harmlessly. */
            int size = getBufIfOpen().length;
            if (len >= size && markpos == -1) {
                return getInIfOpen().read(b, off, len);
            }
            fill();
            avail = count - pos;
            if (avail <= 0) return -1;
        }
        int cnt = (avail < len) ? avail : len;
        System.arraycopy(getBufIfOpen(), pos, b, off, cnt);
        pos += cnt;
        return cnt;
    }

    /**
     * Reads bytes from this byte-input stream into the specified byte array,
     * starting at the given offset.
     *
     * <p> This method implements the general contract of the corresponding
     * {@link InputStream#read(byte[], int, int) read} method of
     * the {@link InputStream} class.  As an additional
     * convenience, it attempts to read as many bytes as possible by repeatedly
     * invoking the {@code read} method of the underlying stream.  This
     * iterated {@code read} continues until one of the following
     * conditions becomes true: <ul>
     *
     *   <li> The specified number of bytes have been read,
     *
     *   <li> The {@code read} method of the underlying stream returns
     *   {@code -1}, indicating end-of-file, or
     *
     *   <li> The {@code available} method of the underlying stream
     *   returns zero, indicating that further input requests would block.
     *
     * </ul> If the first {@code read} on the underlying stream returns
     * {@code -1} to indicate end-of-file then this method returns
     * {@code -1}.  Otherwise, this method returns the number of bytes
     * actually read.
     *
     * <p> Subclasses of this class are encouraged, but not required, to
     * attempt to read as many bytes as possible in the same fashion.
     *
     * @param      b     destination buffer.
     * @param      off   offset at which to start storing bytes.
     * @param      len   maximum number of bytes to read.
     * @return     the number of bytes read, or {@code -1} if the end of
     *             the stream has been reached.
     * @throws     IOException  if this input stream has been closed by
     *                          invoking its {@link #close()} method,
     *                          or an I/O error occurs.
     * @throws     IndexOutOfBoundsException {@inheritDoc}
     */
    public int read(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        if ((off | len | (off + len) | (b.length - (off + len))) < 0) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        int n = 0;
        for (;;) {
            int nread = read1(b, off + n, len - n);
            if (nread <= 0)
                return (n == 0) ? nread : n;
            n += nread;
            if (n >= len)
                return n;
            // if not closed but no bytes available, return
            InputStream input = in;
            if (input != null && input.available() <= 0)
                return n;
        }
    }

    /**
     * See the general contract of the {@code skip}
     * method of {@code InputStream}.
     *
     * @throws IOException  if this input stream has been closed by
     *                      invoking its {@link #close()} method,
     *                      {@code in.skip(n)} throws an IOException,
     *                      or an I/O error occurs.
     */
    public long skip(long n) throws IOException {
        ensureOpen();
        if (n <= 0) {
            return 0;
        }
        long avail = count - pos;

        if (avail <= 0) {
            // If no mark position set then don't keep in buffer
            if (markpos == -1)
                return getInIfOpen().skip(n);

            // Fill in buffer to save bytes for reset
            fill();
            avail = count - pos;
            if (avail <= 0)
                return 0;
        }

        long skipped = (avail < n) ? avail : n;
        pos += (int)skipped;
        return skipped;
    }

    /**
     * Returns an estimate of the number of bytes that can be read (or
     * skipped over) from this input stream without blocking by the next
     * invocation of a method for this input stream. The next invocation might be
     * the same thread or another thread.  A single read or skip of this
     * many bytes will not block, but may read or skip fewer bytes.
     * <p>
     * This method returns the sum of the number of bytes remaining to be read in
     * the buffer ({@code count - pos}) and the result of calling the
     * {@link java.io.FilterInputStream#in in}{@code .available()}.
     *
     * @return     an estimate of the number of bytes that can be read (or skipped
     *             over) from this input stream without blocking.
     * @throws     IOException  if this input stream has been closed by
     *                          invoking its {@link #close()} method,
     *                          or an I/O error occurs.
     */
    public int available() throws IOException {
        int n = count - pos;
        int avail = getInIfOpen().available();
        return n > (Integer.MAX_VALUE - avail)
                    ? Integer.MAX_VALUE
                    : n + avail;
    }

    /**
     * See the general contract of the {@code mark}
     * method of {@code InputStream}.
     *
     * @param   readlimit   the maximum limit of bytes that can be read before
     *                      the mark position becomes invalid.
     * @see     java.io.BufferedInputStream#reset()
     */
    public void mark(int readlimit) {
        marklimit = readlimit;
        markpos = pos;
    }

    /**
     * See the general contract of the {@code reset}
     * method of {@code InputStream}.
     * <p>
     * If {@code markpos} is {@code -1}
     * (no mark has been set or the mark has been
     * invalidated), an {@code IOException}
     * is thrown. Otherwise, {@code pos} is
     * set equal to {@code markpos}.
     *
     * @throws     IOException  if this stream has not been marked or,
     *                  if the mark has been invalidated, or the stream
     *                  has been closed by invoking its {@link #close()}
     *                  method, or an I/O error occurs.
     * @see        java.io.BufferedInputStream#mark(int)
     */
    public void reset() throws IOException {
        ensureOpen();
        if (markpos < 0)
            throw new IOException("Resetting to invalid mark");
        pos = markpos;
    }

    /**
     * Tests if this input stream supports the {@code mark}
     * and {@code reset} methods. The {@code markSupported}
     * method of {@code BufferedInputStream} returns
     * {@code true}.
     *
     * @return  a {@code boolean} indicating if this stream type supports
     *          the {@code mark} and {@code reset} methods.
     * @see     java.io.InputStream#mark(int)
     * @see     java.io.InputStream#reset()
     */
    public boolean markSupported() {
        return true;
    }

    /**
     * Closes this input stream and releases any system resources
     * associated with the stream.
     * Once the stream has been closed, further read(), available(), reset(),
     * or skip() invocations will throw an IOException.
     * Closing a previously closed stream has no effect.
     *
     * @throws     IOException  if an I/O error occurs.
     */
    public void close() throws IOException {
        if(buf!=null) {
            buf = null;
            super.close();
        }
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        int avail = count - pos;
        if (avail > 0) {
            // Prevent poisoning and leaking of buf
            byte[] buffer = Arrays.copyOfRange(getBufIfOpen(), pos, count);
            out.write(buffer,pos,count-pos);
            pos = count;
        }
        return super.transferTo(out);
    }
}
