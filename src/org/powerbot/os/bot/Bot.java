package org.powerbot.os.bot;

import java.applet.Applet;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.io.Closeable;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

import org.powerbot.os.api.ClientContext;
import org.powerbot.os.api.wrappers.GameObject;
import org.powerbot.os.api.wrappers.RelativePosition;
import org.powerbot.os.bot.event.EventDispatcher;
import org.powerbot.os.bot.event.PaintListener;
import org.powerbot.os.bot.loader.GameAppletLoader;
import org.powerbot.os.bot.loader.GameCrawler;
import org.powerbot.os.bot.loader.GameLoader;
import org.powerbot.os.bot.loader.GameStub;
import org.powerbot.os.client.Client;
import org.powerbot.os.gui.BotChrome;

public class Bot implements Runnable, Closeable {
	public final EventDispatcher dispatcher;
	public final ClientContext ctx;
	private final BotChrome chrome;
	private final ThreadGroup group;
	private Applet applet;
	private Client client;

	public Bot(final BotChrome chrome) {
		this.chrome = chrome;
		group = new ThreadGroup(getClass().getSimpleName());
		dispatcher = new EventDispatcher();
		ctx = ClientContext.newContext(this);
	}

	@Override
	public void run() {
		final GameCrawler crawler = new GameCrawler();
		if (!crawler.call()) {
			return;
		}

		final GameLoader game = new GameLoader(crawler);
		final ClassLoader classLoader = game.call();
		if (classLoader == null) {
			return;
		}

		final GameAppletLoader loader = new GameAppletLoader(game, classLoader);
		loader.setCallback(new Runnable() {
			@Override
			public void run() {
				hook(loader);
			}
		});
		final Thread t = new Thread(group, loader);
		t.setContextClassLoader(classLoader);
		t.start();
	}

	private void hook(final GameAppletLoader loader) {
		applet = loader.getApplet();
		client = (Client) loader.getClient();
		final GameCrawler crawler = loader.getGameLoader().crawler;
		final GameStub stub = new GameStub(crawler.parameters, crawler.archive);
		applet.setStub(stub);
		applet.init();

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				Dimension d = null;
				final Map<String, String> p = crawler.properties;
				if (p.containsKey("width") && p.containsKey("height")) {
					try {
						d = new Dimension(Integer.parseInt(p.get("width")), Integer.parseInt(p.get("height")));
					} catch (final NumberFormatException ignored) {
					}
				}
				d = d == null ? chrome.getSize() : d;

				applet.setSize(d);
				applet.setMinimumSize(d);
				chrome.add(applet);
				chrome.panel.setVisible(false);
				applet.start();
			}
		});

		debug();
	}

	private void debug() {
		new Thread(group, dispatcher, dispatcher.getClass().getName()).start();
		ctx.setClient(client);
		dispatcher.add(new PaintListener() {
			@Override
			public void repaint(final Graphics render) {
				final List<GameObject> list = ctx.objects.getLoaded();
				for (final GameObject o : list) {
					render.setColor(Color.green);
					for (int i = 0; i < o.getType(); i++) {
						render.setColor(render.getColor().darker());
					}
					final RelativePosition r = o.getRelativePosition();
					final Point p = ctx.game.worldToScreen(r.x, r.z, 0);
					render.drawString(String.format("%d", o.getId()), p.x, p.y);
				}
			}
		});
	}

	@Override
	public void close() {
		if (applet != null) {
			applet.stop();
			applet.destroy();
		}
		group.interrupt();
	}
}
