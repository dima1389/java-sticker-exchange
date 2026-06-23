// The "client.app" package holds the client program's starting point.
package com.stickerexchange.client.app;

// The controller coordinates between the UI and the network (the "brain" of the client side).
import com.stickerexchange.client.controller.ClientController;
// The main window the user sees and interacts with.
import com.stickerexchange.client.ui.StickerExchangeFrame;

/**
 * ClientMain — the program that STARTS the client (the desktop app the user runs).
 *
 * <p>WHAT IT DOES: it wires together the three big pieces of the client and shows the window. This is
 * a great example of the MVC pattern (Model-View-Controller):
 * <ul>
 *   <li>VIEW — {@link StickerExchangeFrame}, the on-screen window.</li>
 *   <li>CONTROLLER — {@link ClientController}, which reacts to user actions and server messages.</li>
 *   <li>MODEL — the shared data classes (AlbumState, TradeMatch, ...) the other two work with.</li>
 * </ul>
 *
 * <p>KEY CONCEPT — separation of concerns: keeping the window, the logic, and the data in separate
 * classes makes each one simpler to understand and change.
 */
// Entry-point class; "final" prevents extension.
public final class ClientMain {
    // Private constructor: this class only holds the "main" launcher and is never turned into an object.
    private ClientMain() {
    }

    // The first method Java runs when starting the client program.
    public static void main(String[] args) {
        // Create the window (the View).
        StickerExchangeFrame frame = new StickerExchangeFrame();
        // Create the controller and tell it which window to drive.
        ClientController controller = new ClientController(frame);
        // Tell the window which controller to notify when the user clicks buttons. "bind" connects the two.
        frame.bind(controller);
        // Make the window appear on screen so the user can use it.
        frame.showWindow();
    }
}
