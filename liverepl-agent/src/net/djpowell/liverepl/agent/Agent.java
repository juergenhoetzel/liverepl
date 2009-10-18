package net.djpowell.liverepl.agent;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import net.djpowell.liverepl.client.Main;

public class Agent {
	
	private static ClassLoader pushClassLoader(List<URL> urls)  {
		TRC.fine("Creating new classloader with: " + urls);
		ClassLoader old = Thread.currentThread().getContextClassLoader();
		TRC.fine("Old classloader: " + old);
		URLClassLoader withClojure = new URLClassLoader(urls.toArray(new URL[urls.size()]), old); // TODO
		Thread.currentThread().setContextClassLoader(withClojure);
		return old;
	}
	
	private static void popClassLoader(ClassLoader old) {
		TRC.fine("Restoring old context classloader");
		Thread.currentThread().setContextClassLoader(old);
	}

	private static boolean isClojureLoaded() {
		try {
			ClassLoader cl = Thread.currentThread().getContextClassLoader();
			cl.loadClass("clojure.lang.RT");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}
	
	public static void agentmain(String agentArgs, Instrumentation inst) {
		TRC.fine("Started Attach agent");
		
		StringTokenizer stok = new StringTokenizer(agentArgs, "\n");
		if (stok.countTokens() != 3) {
			throw new RuntimeException("Invalid parameters: " + agentArgs);
		}
		
		int port = Integer.parseInt(stok.nextToken());
		TRC.fine("Port: " + port);
		String clojurePath = stok.nextToken();
		String serverPath = stok.nextToken();
		
		boolean clojureLoaded = isClojureLoaded();
		TRC.fine("Clojure is " + (clojureLoaded ? "" : "not ") + "loaded");

		List<URL> urls;
		if (clojureLoaded) {
			urls = getJarUrls(serverPath);
		} else {
			urls = getJarUrls(clojurePath, serverPath);
		}
		
		ClassLoader old = pushClassLoader(urls);
		try {
			if (!clojureLoaded) { // if clojure wasn't loaded before, print current status
				TRC.fine("Clojure is " + (isClojureLoaded() ? "" : "not ") + "loaded");
			}
			startRepl(port);
		} finally {
			popClassLoader(old);
		}
	}

	private static List<URL> getJarUrls(String... paths) {
		List<URL> urls = new ArrayList<URL>();
		try {
			for (String path : paths) {
				URL url = new File(path).toURI().toURL();
				urls.add(url);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return urls;
	}

	private static void startRepl(int port) {
		// avoids making load-time references to Clojure classes from the system classloader
		try {
			ClassLoader cl = Thread.currentThread().getContextClassLoader();
			Class<?> repl = Class.forName("net.djpowell.liverepl.server.Repl", true, cl);
			Method method = repl.getMethod("main", InetAddress.class, Integer.TYPE);
			method.invoke(null, Main.LOCALHOST, port);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static final Logger TRC = Logger.getLogger(Agent.class.getName()); 

}