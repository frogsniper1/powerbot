package org.powerbot.gui;

import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.DefaultComboBoxModel;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.powerbot.event.PaintListener;
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.wrappers.Drawable;
import org.powerbot.script.wrappers.GameObject;
import org.powerbot.script.wrappers.GroundItem;
import org.powerbot.script.wrappers.Interactive;
import org.powerbot.script.wrappers.Npc;
import org.powerbot.script.wrappers.Player;
import org.powerbot.script.wrappers.Tile;
import org.powerbot.script.wrappers.TileMatrix;

public class BotBoundingUtility extends JDialog implements PaintListener, MouseListener {
	private final BotChrome chrome;
	private final JLabel labelTarget;
	private final AtomicBoolean selecting;
	private final Point point;
	private TargetSelection<Interactive> selection;
	private Interactive target;

	public BotBoundingUtility(final BotChrome chrome) {
		this.chrome = chrome;
		this.selecting = new AtomicBoolean(false);
		this.point = new Point(-1, -1);
		this.selection = null;
		target = null;

		final JLabel labelType = new JLabel("Choose type:");
		final JLabel labelX = new JLabel("X");
		final JLabel labelY = new JLabel("Y");
		final JLabel labelZ = new JLabel("Z");
		final JLabel labelStart = new JLabel("Start");
		final JLabel labelStop = new JLabel("Stop");
		labelTarget = new JLabel("Target: null");

		final JComboBox comboBoxTarget = new JComboBox();
		comboBoxTarget.setModel(new DefaultComboBoxModel(new TargetSelection[]{
				new TargetSelection<Player>("Player", new Callable<Player>() {
					@Override
					public Player call() {
						final MethodContext ctx = chrome.getBot().ctx;
						return (Player) nearest(ctx.players.select());
					}
				}),
				new TargetSelection<Npc>("Npc", new Callable<Npc>() {
					@Override
					public Npc call() {
						final MethodContext ctx = chrome.getBot().ctx;
						return (Npc) nearest(ctx.npcs.select());
					}
				}),
				new TargetSelection<GameObject>("Object", new Callable<GameObject>() {
					@Override
					public GameObject call() {
						final MethodContext ctx = chrome.getBot().ctx;
						return (GameObject) nearest(ctx.objects.select());
					}
				}),
				new TargetSelection<GroundItem>("Ground Item", new Callable<GroundItem>() {
					@Override
					public GroundItem call() {
						final MethodContext ctx = chrome.getBot().ctx;
						return (GroundItem) nearest(ctx.groundItems.select());
					}
				}),
				new TargetSelection<TileMatrix>("Tile", new Callable<TileMatrix>() {
					@Override
					public TileMatrix call() {
						final MethodContext ctx = chrome.getBot().ctx;
						final List<TileMatrix> list = new ArrayList<TileMatrix>();
						final Tile t = ctx.players.local().getLocation();
						for (int x = -20; x <= 20; x++) {
							for (int y = -20; y <= 20; y++) {
								list.add(t.derive(x, y).getMatrix(ctx));
							}
						}
						return (TileMatrix) nearest(list);
					}
				})
		}));
		comboBoxTarget.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent actionEvent) {
				selection = (TargetSelection<Interactive>) comboBoxTarget.getSelectedItem();
			}
		});

		final int min = -5120, max = 5120, step = 4;
		final SpinnerNumberModel
				modelX1 = new SpinnerNumberModel(-256, min, max, step),
				modelY1 = new SpinnerNumberModel(-512, min, max, step),
				modelZ1 = new SpinnerNumberModel(-256, min, max, step),
				modelX2 = new SpinnerNumberModel(256, min, max, step),
				modelY2 = new SpinnerNumberModel(0, min, max, step),
				modelZ2 = new SpinnerNumberModel(256, min, max, step);
		final ChangeListener l = new ChangeListener() {
			@Override
			public void stateChanged(final ChangeEvent changeEvent) {
				if (target != null) {
					target.setBounds(
							modelX1.getNumber().intValue(), modelX2.getNumber().intValue(),
							modelY1.getNumber().intValue(), modelY2.getNumber().intValue(),
							modelZ1.getNumber().intValue(), modelZ2.getNumber().intValue()
					);
				}
			}
		};
		final JSpinner spinnerStartX = new JSpinner(modelX1);
		spinnerStartX.addChangeListener(l);
		final JSpinner spinnerStartY = new JSpinner(modelY1);
		spinnerStartY.addChangeListener(l);
		final JSpinner spinnerStartZ = new JSpinner(modelZ1);
		spinnerStartZ.addChangeListener(l);
		final JSpinner spinnerEndX = new JSpinner(modelX2);
		spinnerEndX.addChangeListener(l);
		final JSpinner spinnerEndY = new JSpinner(modelY2);
		spinnerEndY.addChangeListener(l);
		final JSpinner spinnerEndZ = new JSpinner(modelZ2);
		spinnerEndZ.addChangeListener(l);
		l.stateChanged(null);

		final JButton buttonExit = new JButton("Exit");
		final JButton buttonCopy = new JButton("Copy to Clipboard");
		final JButton buttonSelect = new JButton("Begin Select");
		buttonSelect.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent actionEvent) {
				selecting.set(true);
			}
		});
		final JButton buttonReset = new JButton("Reset");
		buttonReset.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent actionEvent) {
				modelX1.setValue(-256);
				modelX2.setValue(256);
				modelY1.setValue(-512);
				modelY2.setValue(0);
				modelZ1.setValue(-256);
				modelZ2.setValue(256);
			}
		});

		chrome.getBot().dispatcher.add(this);
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(final WindowEvent e) {
				setVisible(false);
				chrome.getBot().dispatcher.remove(BotBoundingUtility.this);
				dispose();
			}
		});

		final GroupLayout layout = new GroupLayout(getContentPane());
		getContentPane().setLayout(layout);
		layout.setHorizontalGroup(
				layout.createParallelGroup(GroupLayout.Alignment.LEADING)
						.addGroup(layout.createSequentialGroup()
								.addContainerGap()
								.addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
										.addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
												.addGap(0, 0, Short.MAX_VALUE)
												.addComponent(buttonReset)
												.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
												.addComponent(buttonSelect)
												.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
												.addComponent(buttonCopy)
												.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
												.addComponent(buttonExit)
												.addGap(10, 10, 10))
										.addGroup(layout.createSequentialGroup()
												.addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
														.addComponent(comboBoxTarget, GroupLayout.PREFERRED_SIZE, 100, GroupLayout.PREFERRED_SIZE)
														.addGroup(layout.createSequentialGroup()
																.addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
																		.addComponent(labelType, GroupLayout.PREFERRED_SIZE, 100, GroupLayout.PREFERRED_SIZE)
																		.addGroup(layout.createSequentialGroup()
																				.addComponent(labelY)
																				.addGap(18, 18, 18)
																				.addComponent(spinnerStartY, GroupLayout.PREFERRED_SIZE, 75, GroupLayout.PREFERRED_SIZE))
																		.addGroup(layout.createSequentialGroup()
																				.addComponent(labelZ)
																				.addGap(18, 18, 18)
																				.addComponent(spinnerStartZ, GroupLayout.PREFERRED_SIZE, 75, GroupLayout.PREFERRED_SIZE))
																		.addGroup(layout.createSequentialGroup()
																				.addComponent(labelX)
																				.addGap(18, 18, 18)
																				.addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
																						.addComponent(labelStart)
																						.addComponent(spinnerStartX, GroupLayout.PREFERRED_SIZE, 75, GroupLayout.PREFERRED_SIZE))))
																.addGap(18, 18, 18)
																.addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
																		.addComponent(labelStop)
																		.addComponent(labelTarget)
																		.addComponent(spinnerEndX, GroupLayout.PREFERRED_SIZE, 75, GroupLayout.PREFERRED_SIZE)
																		.addComponent(spinnerEndY, GroupLayout.PREFERRED_SIZE, 75, GroupLayout.PREFERRED_SIZE)
																		.addComponent(spinnerEndZ, GroupLayout.PREFERRED_SIZE, 75, GroupLayout.PREFERRED_SIZE))))
												.addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
		);
		layout.setVerticalGroup(
				layout.createParallelGroup(GroupLayout.Alignment.LEADING)
						.addGroup(layout.createSequentialGroup()
								.addContainerGap()
								.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
										.addComponent(labelType)
										.addComponent(labelTarget))
								.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
								.addComponent(comboBoxTarget, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
								.addGap(18, 18, 18)
								.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
										.addComponent(labelStart, GroupLayout.PREFERRED_SIZE, 14, GroupLayout.PREFERRED_SIZE)
										.addComponent(labelStop))
								.addGap(18, 18, 18)
								.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
										.addComponent(labelX)
										.addComponent(spinnerStartX, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
										.addComponent(spinnerEndX, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
								.addGap(18, 18, 18)
								.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
										.addComponent(labelY)
										.addComponent(spinnerStartY, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
										.addComponent(spinnerEndY, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
								.addGap(18, 18, 18)
								.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
										.addComponent(labelZ)
										.addComponent(spinnerStartZ, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
										.addComponent(spinnerEndZ, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
								.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
								.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
										.addComponent(buttonExit)
										.addComponent(buttonCopy)
										.addComponent(buttonSelect)
										.addComponent(buttonReset))
								.addContainerGap())
		);

		pack();
	}

	private Interactive nearest(final Iterable<? extends Interactive> list) {
		Interactive r = null;
		double d = Double.MAX_VALUE;
		for (final Interactive interactive : list) {
			final Point p = interactive.getCenterPoint();
			final double d2 = p.distance(point);
			if (d2 < d) {
				d = d2;
				r = interactive;
			}
		}
		return r;
	}

	@Override
	public void repaint(final Graphics render) {
		if (target != null && target instanceof Drawable) {
			((Drawable) target).draw(render);
		}
	}

	@Override
	public void mouseClicked(final MouseEvent mouseEvent) {
	}

	@Override
	public void mousePressed(final MouseEvent mouseEvent) {
		if (!selecting.compareAndSet(true, false)) {
			return;
		}
		point.move(mouseEvent.getX(), mouseEvent.getY());
		if (selection != null) {
			try {
				target = selection.callable.call();
			} catch (final Exception ignored) {
				target = null;
			}
			labelTarget.setText("Target: " + target);
		}
	}

	@Override
	public void mouseReleased(final MouseEvent mouseEvent) {
	}

	@Override
	public void mouseEntered(final MouseEvent mouseEvent) {
	}

	@Override
	public void mouseExited(final MouseEvent mouseEvent) {
	}

	private final class TargetSelection<K> {
		private final String str;
		public final Callable<K> callable;

		private TargetSelection(final String str, final Callable<K> callable) {
			this.str = str;
			this.callable = callable;
		}

		@Override
		public String toString() {
			return str;
		}
	}
}