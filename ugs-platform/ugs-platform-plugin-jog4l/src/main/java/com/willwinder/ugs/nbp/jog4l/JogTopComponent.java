/*
    Copyright 2018-2023 Will Winder

    This file is part of Universal Gcode Sender (UGS).

    UGS is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    UGS is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with UGS.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.willwinder.ugs.nbp.jog4l;

import com.willwinder.ugs.nbp.jog4l.actions.ShowABCStepSizeAction;
import com.willwinder.ugs.nbp.jog4l.actions.UseSeparateStepSizeAction;
import com.willwinder.ugs.nbp.lib.Mode;
import com.willwinder.ugs.nbp.lib.lookup.CentralLookup;
import com.willwinder.ugs.nbp.lib.services.LocalizingService;
import com.willwinder.universalgcodesender.listeners.UGSEventListener;
import com.willwinder.universalgcodesender.model.Axis;
import com.willwinder.universalgcodesender.model.BackendAPI;
import com.willwinder.universalgcodesender.model.PartialPosition;
import com.willwinder.universalgcodesender.model.UGSEvent;
import com.willwinder.universalgcodesender.model.UnitUtils;
import com.willwinder.universalgcodesender.model.events.ControllerStateEvent;
import com.willwinder.universalgcodesender.model.events.SettingChangedEvent;
import com.willwinder.universalgcodesender.services.JogService;
import com.willwinder.universalgcodesender.utils.ContinuousJogWorker;
import com.willwinder.universalgcodesender.utils.Settings;
import com.willwinder.universalgcodesender.utils.SwingHelpers;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;

import javax.swing.JPopupMenu;
import java.awt.BorderLayout;

/**
 * The jog control panel in NetBeans
 *
 * @author Joacim Breiler
 */
@TopComponent.Description(
        preferredID = "Jog4lTopComponent"
)
@TopComponent.Registration(
        mode = Mode.LEFT_BOTTOM,
        openAtStartup = true,
        position = 5000)
@ActionID(
        category = JogTopComponent.CATEGORY,
        id = JogTopComponent.ACTION_ID)
@ActionReference(
        path = JogTopComponent.WINOW_PATH)
@TopComponent.OpenActionRegistration(
        displayName = "Jog 4L Controller",
        preferredID = "Jog4lTopComponent"
)
public final class JogTopComponent extends TopComponent implements UGSEventListener, JogPanelListener {

    public static final String WINOW_PATH = LocalizingService.MENU_WINDOW_PLUGIN;
    public static final String CATEGORY = LocalizingService.CATEGORY_WINDOW;
    public static final String ACTION_ID = "com.willwinder.ugs.nbp.jog4l.JogTopComponent";

    private final BackendAPI backend;
    private final JogPanel jogPanel;
    private final JogService jogService;
    private final ContinuousJogWorker continuousJogWorker;

    private boolean ignoreLongClick = false;
    
    private boolean axesAreLinked = false;

    public JogTopComponent() {
        backend = CentralLookup.getDefault().lookup(BackendAPI.class);
        jogService = CentralLookup.getDefault().lookup(JogService.class);
        continuousJogWorker = new ContinuousJogWorker(backend, jogService);
        UseSeparateStepSizeAction separateStepSizeAction = Lookup.getDefault().lookup(UseSeparateStepSizeAction.class);
        ShowABCStepSizeAction showABCStepSizeAction = Lookup.getDefault().lookup(ShowABCStepSizeAction.class);

        jogPanel = new JogPanel();
        jogPanel.setEnabled(jogService.canJog());
        updateSettings();
        jogPanel.addListener(this);

        backend.addUGSEventListener(this);

        setLayout(new BorderLayout());
        add(jogPanel, BorderLayout.CENTER);

        // Right click options
        if (separateStepSizeAction != null || showABCStepSizeAction != null) {
            JPopupMenu popupMenu = new JPopupMenu();
            if (separateStepSizeAction != null) {
                popupMenu.add(separateStepSizeAction);
            }
            if (showABCStepSizeAction != null) {
                popupMenu.add(showABCStepSizeAction);
            }
            SwingHelpers.traverse(this, (comp) -> comp.setComponentPopupMenu(popupMenu));
        }
    }

    @Override
    protected void componentClosed() {
        super.componentClosed();
        backend.removeUGSEventListener(this);
        continuousJogWorker.destroy();
    }

    @Override
    protected void componentOpened() {
        super.componentOpened();
        setName(LocalizingService.JogControlTitle);
        setToolTipText(LocalizingService.JogControlTooltip);
        updateControls();
        updateSettings();
    }

    @Override
    public void UGSEvent(UGSEvent event) {
        if (event instanceof ControllerStateEvent || event instanceof SettingChangedEvent) {
            updateSettings();
            updateControls();
        }
    }

    private void updateSettings() {
        jogPanel.setFeedRate(Double.valueOf(jogService.getFeedRate()).intValue());
        jogPanel.setStepSizeHorz(jogService.getStepSizeXY());
        jogPanel.setStepSizeVert(jogService.getStepSizeZ());
        jogPanel.setStepSizeRot(jogService.getStepSizeABC());
        jogPanel.setUnit(jogService.getUnits());
        jogPanel.enabledStepSizes(jogService.useStepSizeZ(), jogService.showABCStepSize());

        checkAxisEnabled(Axis.A);
        checkAxisEnabled(Axis.B);
        checkAxisEnabled(Axis.C);
    }

    private void checkAxisEnabled(Axis axis) {
        Settings settings = backend.getSettings();
        jogPanel.setButtonsVisible(axis, settings.isAxisEnabled(axis) && backend.getController() != null && backend.getController().getCapabilities().hasAxis(axis));
    }

    private void updateControls() {
        jogPanel.setEnabled(jogService.canJog());
    }

    private void adjustManualLocationXYZABC(int x, int y, int z, int a, int b, int c ) {
        Settings settings = backend.getSettings();
        double horzStepSize = settings.getManualModeStepSize();
        double vertStepSize = settings.getZJogStepSize();
        double rotStepSize = settings.getABCJogStepSize();
        
        
        PartialPosition adjustment = new PartialPosition(
                x == 0 ? null : x * horzStepSize,
                y == 0 ? null : y * vertStepSize,
                z == 0 ? null : z * vertStepSize,
                a == 0 ? null : a * horzStepSize,
                b == 0 ? null : b * rotStepSize,
                c == 0 ? null : c * rotStepSize,
                backend.getSettings().getPreferredUnits());
        
        jogService.adjustManualLocation(adjustment, 1);
    }
            
    @Override
    public void onJogButtonClicked(JogPanelButtonEnum button) {
        // Ignore the "click" event when a long button press is released
        if (ignoreLongClick) {
            ignoreLongClick = false;
            return;
        }

        switch (button) {
            case BUTTON_XNEG:
                if (axesAreLinked) {
                    adjustManualLocationXYZABC(-1, 0, 0, -1, 0, 0);
                }
                else {
                    adjustManualLocationXYZABC(-1, 0, 0, 0, 0, 0);
                }
                break;
            case BUTTON_XPOS:
                if (axesAreLinked) {
                    adjustManualLocationXYZABC(1, 0, 0, 1, 0, 0);
                }
                else {
                    adjustManualLocationXYZABC(1, 0, 0, 0, 0, 0);
                }
                break;
            case BUTTON_YNEG:
                if (axesAreLinked) {
                    adjustManualLocationXYZABC(0, -1, -1, 0, 0, 0);
                }
                else {
                    adjustManualLocationXYZABC(0, -1, 0, 0, 0, 0);
                }
                break;
            case BUTTON_YPOS:
                if (axesAreLinked) {
                    adjustManualLocationXYZABC(0, 1, 1, 0, 0, 0);
                }
                else {
                    adjustManualLocationXYZABC(0, 1, 0, 0, 0, 0);
                }
                break;
            case BUTTON_DIAG_XNEG_YNEG:
                if (axesAreLinked) {
                    adjustManualLocationXYZABC(-1, -1, -1, -1, 0, 0);
                }
                else {
                    adjustManualLocationXYZABC(-1, -1, 0, 0, 0, 0);
                }
                break;
            case BUTTON_DIAG_XNEG_YPOS:
                if (axesAreLinked) {
                    adjustManualLocationXYZABC(-1, 1, 1, -1, 0, 0);
                }
                else {
                    adjustManualLocationXYZABC(-1, 1, 0, 0, 0, 0);
                }
                break;
            case BUTTON_DIAG_XPOS_YNEG:
                if (axesAreLinked) {
                    adjustManualLocationXYZABC(1, -1, -1, 1, 0, 0);
                }
                else {
                    adjustManualLocationXYZABC(1, -1, 0, 0, 0, 0);
                }
                break;
            case BUTTON_DIAG_XPOS_YPOS:
                if (axesAreLinked) {
                    adjustManualLocationXYZABC(1, 1, 1, 1, 0, 0);
                }
                else {
                    adjustManualLocationXYZABC(1, 1, 0, 0, 0, 0);
                }
                break;
            case BUTTON_ZNEG:
                if (axesAreLinked) {
                    adjustManualLocationXYZABC(0, -1, -1, 0, 0, 0);
                }
                else {
                    adjustManualLocationXYZABC(0, 0, -1, 0, 0, 0);
                }
                break;
            case BUTTON_ZPOS:
                if (axesAreLinked) {
                    adjustManualLocationXYZABC(0, 1, 1, 0, 0, 0);
                }
                else {
                    adjustManualLocationXYZABC(0, 0, 1, 0, 0, 0);
                }
                break;
            case BUTTON_ANEG:
                if (axesAreLinked) {
                    adjustManualLocationXYZABC(-1, 0, 0, -1, 0, 0);
                }
                else {
                    adjustManualLocationXYZABC(0, 0, 0, -1, 0, 0);
                }
                break;
            case BUTTON_APOS:
                if (axesAreLinked) {
                    adjustManualLocationXYZABC(1, 0, 0, 1, 0, 0);
                }
                else {
                    adjustManualLocationXYZABC(0, 0, 0, 1, 0, 0);
                }
                break;

            case BUTTON_DIAG_ANEG_ZNEG:
                if (axesAreLinked) {
                    adjustManualLocationXYZABC(-1, -1, -1, -1, 0, 0);
                }
                else {
                    adjustManualLocationXYZABC(0, 0, -1, -1, 0, 0);
                }
                break;
            case BUTTON_DIAG_ANEG_ZPOS:
                if (axesAreLinked) {
                    adjustManualLocationXYZABC(-1, 1, 1, -1, 0, 0);
                }
                else {
                    adjustManualLocationXYZABC(0, 0, 1, -1, 0, 0);
                }
                break;
            case BUTTON_DIAG_APOS_ZNEG:
                if (axesAreLinked) {
                    adjustManualLocationXYZABC(1, -1, -1, 1, 0, 0);
                }
                else {
                    adjustManualLocationXYZABC(0, 0, -1, 1, 0, 0);
                }
                break;
            case BUTTON_DIAG_APOS_ZPOS:
                if (axesAreLinked) {
                    adjustManualLocationXYZABC(1, 1, 1, 1, 0, 0);
                }
                else {
                    adjustManualLocationXYZABC(0, 0, 1, 1, 0, 0);
                }
                break;

            case BUTTON_BNEG:
                adjustManualLocationXYZABC(0, 0, 0, 0, -1, 0);
                break;
            case BUTTON_BPOS:
                adjustManualLocationXYZABC(0, 0, 0, 0, 1, 0);
                break;
            case BUTTON_CNEG:
                adjustManualLocationXYZABC(0, 0, 0, 0, 0, -1);
                break;
            case BUTTON_CPOS:
                adjustManualLocationXYZABC(0, 0, 0, 0, 0, 1);
                break;
            default:
        }
    }

    @Override
    public void onJogButtonLongPressed(JogPanelButtonEnum button) {
        if (backend.getController().getCapabilities().hasContinuousJogging()) {
            // set flag so when we release the long press we don't add
            // an extra jog step through the click event
            ignoreLongClick = true;

            continuousJogWorker.setDirection(button.getX(), button.getY(), button.getZ(), button.getA(), button.getB(), button.getC());
            
            if (axesAreLinked) {
                continuousJogWorker.setDirection(button.getX() + button.getA(), button.getY() + button.getZ(), button.getZ() + button.getY(), button.getA() + button.getX(), button.getB(), button.getC());
            }
            else {
                continuousJogWorker.setDirection(button.getX(), button.getY(), button.getZ(), button.getA(), button.getB(), button.getC());
            }
                
            continuousJogWorker.start();
        }
    }

    @Override
    public void onJogButtonLongReleased(JogPanelButtonEnum button) {
        continuousJogWorker.stop();
    }

    @Override
    public void onStepSizeVertChanged(double value) {
        jogService.setStepSizeZ(value);
    }

    @Override
    public void onStepSizeHorzChanged(double value) {
        jogService.setStepSizeXY(value);
    }

    @Override
    public void onStepSizeRotChanged(double value) {
        jogService.setStepSizeABC(value);
    }

    @Override
    public void onFeedRateChanged(int value) {
        jogService.setFeedRate(value);
    }

    @Override
    public void onToggleUnit() {
        if (jogService.getUnits() == UnitUtils.Units.MM) {
            jogService.setUnits(UnitUtils.Units.INCH);
        } else {
            jogService.setUnits(UnitUtils.Units.MM);
        }
    }

    @Override
    public void onToggleLinkedAxes() {
        axesAreLinked = !axesAreLinked;
        jogPanel.setLinkedAxesStatus(axesAreLinked);
    }

    @Override
    public void onIncreaseStepSize() {
        jogService.multiplyXYStepSize();
        jogService.multiplyZStepSize();
        jogService.multiplyABCStepSize();
    }

    @Override
    public void onDecreaseStepSize() {
        jogService.divideXYStepSize();
        jogService.divideZStepSize();
        jogService.divideABCStepSize();
    }
}
