package bata.protocol;

import bata.model.Room;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

public class RoomRecorder implements ActionListener {
    private Room currentRoom;
    private final Consumer<Room> onRoomChange;

    public RoomRecorder(Consumer<Room> onRoomChange) {
        this.currentRoom = null;
        this.onRoomChange = onRoomChange;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Room room = Room.fromFrame(e.getActionCommand());
        this.currentRoom = room;
        this.onRoomChange.accept(room);
    }

    public Room getCurrentRoom() {
        return currentRoom;
    }
}
