/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 */
package org.kurento.test.performance.webrtc;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.test.base.PerformanceTest;
import org.kurento.test.client.Browser;
import org.kurento.test.client.BrowserClient;
import org.kurento.test.client.Client;
import org.kurento.test.client.WebRtcChannel;
import org.kurento.test.client.WebRtcMode;
import org.kurento.test.latency.LatencyException;
import org.kurento.test.monitor.SystemMonitorManager;
import org.kurento.test.services.Node;

/**
 * <strong>Description</strong>: WebRTC (in loopback) test with Selenium Grid.<br/>
 * <strong>Pipeline</strong>:
 * <ul>
 * <li>WebRtcEndpoint -> WebRtcEndpoint</li>
 * </ul>
 * <strong>Pass criteria</strong>:
 * <ul>
 * <li>No latency problems detected test startup</li>
 * </ul>
 *
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @since 5.0.5
 */
public class WebRtcPerformanceLatencyTest extends PerformanceTest {

	private static final int DEFAULT_NODES = 25; // Number of nodes
	private static final int DEFAULT_NBROWSERS = 4; // Browser per node
	private static final int DEFAULT_CLIENT_RATE = 5000; // milliseconds
	private static final int DEFAULT_MONITOR_RATE = 1000; // milliseconds
	private static final int DEFAULT_HOLD_TIME = 30000; // milliseconds
	private static final int DEFAULT_TIMEOUT = 60; // milliseconds

	private SystemMonitorManager monitor;
	public int numBrowsers;
	public int numNodes;
	public int clientRate;
	public int holdTime;
	public int monitorRate;

	public WebRtcPerformanceLatencyTest() {
		nodes = new ArrayList<Node>();

		numNodes = Integer.parseInt(System.getProperty(
				"test.webrtcgrid.numnodes", String.valueOf(DEFAULT_NODES)));
		numBrowsers = Integer.parseInt(System.getProperty(
				"test.webrtcgrid.numbrowsers",
				String.valueOf(DEFAULT_NBROWSERS)));
		clientRate = Integer.parseInt(System.getProperty(
				"test.webrtcgrid.clientrate",
				String.valueOf(DEFAULT_CLIENT_RATE)));
		holdTime = Integer.parseInt(System.getProperty(
				"test.webrtcgrid.holdtime", String.valueOf(DEFAULT_HOLD_TIME)));
		monitorRate = Integer
				.parseInt(System.getProperty("test.webrtcgrid.monitor",
						String.valueOf(DEFAULT_MONITOR_RATE)));

		nodes = getRandomNodes(numNodes, Browser.CHROME, getPathTestFiles()
				+ "/video/15sec/rgbHD.y4m", null, numBrowsers);
	}

	@Before
	public void setup() throws IOException, URISyntaxException {
		monitor = new SystemMonitorManager();
		monitor.setSamplingTime(monitorRate);
		monitor.start();
	}

	@After
	public void teardown() throws IOException {
		monitor.stop();
		monitor.writeResults(getDefaultOutputFile("-kms-monitor.csv"));
		monitor.destroy();
	}

	@Ignore
	@Test
	public void tesWebRtcGridChrome() throws InterruptedException {
		final int playTime = (numNodes * numBrowsers * clientRate) + holdTime;
		final ExecutorService internalExec = Executors
				.newFixedThreadPool(numNodes * numBrowsers);
		CompletionService<Void> exec = new ExecutorCompletionService<>(
				internalExec);

		int numBrowser = 0;
		for (final Node node : nodes) {
			for (int i = 1; i <= numBrowsers; i++) {
				final String name = node.getAddress() + "-browser" + i
						+ "-count" + (numBrowser + 1);
				final int sleepNum = numBrowser;
				exec.submit(new Callable<Void>() {
					public Void call() throws InterruptedException, IOException {
						try {
							Thread.currentThread().setName(name);
							Thread.sleep(clientRate * sleepNum);
							log.debug("*** Starting node {} ***", name);
							monitor.incrementNumClients();
							doTest(node, playTime, name);
						} finally {
							monitor.decrementNumClients();
							log.debug("--- Ending client {} ---", name);
						}
						return null;
					}
				});
				numBrowser++;
			}
		}

		for (int i = 1; i <= nodes.size() * numBrowsers; i++) {
			Future<Void> taskFuture = null;
			try {
				taskFuture = exec.take();
				taskFuture.get(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
			} catch (Throwable e) {
				log.error("[[[ {} ]]]", e.getCause().getMessage());
				if (taskFuture != null) {
					taskFuture.cancel(true);
				}
			} finally {
				log.debug("+++ Ending browser #{} +++", i);
			}
		}
	}

	public void doTest(Node node, int playTime, String name)
			throws IOException, InterruptedException {

		long endTimeMillis = System.currentTimeMillis() + playTime;
		MediaPipeline mp = null;
		BrowserClient browser = null;

		try {
			// Media Pipeline
			mp = kurentoClient.createMediaPipeline();
			WebRtcEndpoint webRtcEndpoint = new WebRtcEndpoint.Builder(mp)
					.build();
			webRtcEndpoint.connect(webRtcEndpoint);

			// Browser
			BrowserClient.Builder builder = new BrowserClient.Builder()
					.browser(node.getBrowser()).client(Client.WEBRTC)
					.remoteNode(node);
			if (node.getVideo() != null) {
				builder = builder.video(node.getVideo());
			}
			browser = builder.build();

			log.debug("*** start#1 {}", name);
			browser.subscribeEvents("playing");
			log.debug("### start#2 {}", name);
			browser.initWebRtc(webRtcEndpoint, WebRtcChannel.VIDEO_ONLY,
					WebRtcMode.SEND_RCV);
			log.debug(">>> start#3 {}", name);

			while (true) {
				if (System.currentTimeMillis() > endTimeMillis) {
					break;
				}
				Thread.sleep(100);
				try {
					monitor.addCurrentLatency(browser.getLatency());
				} catch (LatencyException le) {
					// log.error("$$$ " + le.getMessage());
					monitor.incrementLatencyErrors();
				}
			}
		} finally {
			log.debug("<<< finally {}", name);

			// Release Media Pipeline
			if (mp != null) {
				mp.release();
			}
		}
	}
}