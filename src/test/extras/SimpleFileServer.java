/*
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.util.concurrent.*;
import java.util.logging.*;
import java.io.*;
import java.net.*;

import com.sun.net.httpserver.*;

import jdk.test.lib.net.SimpleSSLContext;
import robaho.net.httpserver.LogFilter;
import robaho.net.httpserver.extras.ContentEncoding;
import robaho.net.httpserver.extras.QueryParameters;

/**
 * Implements a basic static content HTTP server
 * which understands text/html, text/plain content types
 *
 * Must be given an abs pathname to the document root.
 * Directory listings together with text + html files
 * can be served.
 *
 * File Server created on files sub-path
 *
 * Echo server created on echo sub-path
 */
public class SimpleFileServer {

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("usage: java SimpleFileServer rootDir port logfilename");
            System.exit(1);
        }
        Logger logger = Logger.getLogger("com.sun.net.httpserver");
        ConsoleHandler ch = new ConsoleHandler();
        logger.setLevel(Level.ALL);
        ch.setLevel(Level.ALL);
        logger.addHandler(ch);

        logger.log(Level.INFO,() -> "using Java version: " + System.getProperty("java.version"));

        String rootDir = args[0];
        int port = Integer.parseInt(args[1]);
        String logfile = args[2];
        HttpServer server;
        if(port==443) {
            var httpsServer = HttpsServer.create(new InetSocketAddress(port), 8192);
            var ctx = new SimpleSSLContext().get();
            httpsServer.setHttpsConfigurator(new HttpsConfigurator (ctx));
            server = httpsServer;
        } else {
            server = HttpServer.create(new InetSocketAddress(port), 8192);
        }
        HttpHandler h = new FileServerHandler(rootDir);
        HttpHandler h1 = new EchoHandler();
        HttpHandler h2 = new DevNullHandler();
        HttpHandler h3 = new HelloWorldHandler();

        HttpContext c = server.createContext("/files", h);
        c.getFilters().add(new LogFilter(new File(logfile)));

        HttpContext c1 = server.createContext("/echo", h1);
        c1.getFilters().add(new LogFilter(new File(logfile)));

        HttpContext c2 = server.createContext("/devnull", h2);
        c2.getFilters().add(new LogFilter(new File(logfile)));

        HttpContext c3 = server.createContext("/", h3);


        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        // server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
    }

    private static class DevNullHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            QueryParameters qp = QueryParameters.decode(ContentEncoding.encoding(exchange.getRequestHeaders()), exchange.getRequestURI().getQuery());
            long size = Long.parseLong(qp.getFirst("size"));
            byte[] buffer = new byte[1024 * 1024];
            exchange.getResponseHeaders().set("content-type", "application/octet-stream");
            exchange.sendResponseHeaders(200, size);
            OutputStream os = exchange.getResponseBody();
            while (size > 0) {
                long len = Math.min(size, buffer.length);
                os.write(buffer, 0, (int) len);
                size -= len;
            }
            os.close();
        }

    }
    private static class HelloWorldHandler implements HttpHandler {
        private static final byte[] bytes = "Hello World".getBytes();
        public void handle(HttpExchange exchange) throws IOException {
            QueryParameters qp = QueryParameters.decode(ContentEncoding.encoding(exchange.getRequestHeaders()), exchange.getRequestURI().getQuery());
            long size = bytes.length;
            exchange.sendResponseHeaders(200, size);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
}
