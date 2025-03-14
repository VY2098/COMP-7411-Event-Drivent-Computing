import static org.junit.Assert.*;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Deque;
import java.util.LinkedList;
import javax.swing.table.DefaultTableModel;

public class GpsGUI_Test {

    @Test
    public void testCalculateDistance() {
        // Create two mock GPS events
        GpsEvent event1 = new GpsEvent("Tracker1", 10.0, 20.0, 1000.0);
        GpsEvent event2 = new GpsEvent("Tracker1", 11.0, 21.0, 2000.0);

        // Call calculateDistance method
        double distance = GpsGUI.calculateDistance(event1, event2);

        // Verify the calculated distance is correct
        assertEquals(156239.26807158336, distance, 0.001);
    }

    @Test
    public void testUpdateTotalDistance() {
        // Mock tracker data map and right table model
        Deque<TimestampedGpsEvent> gpsEvents = new LinkedList<>();
        gpsEvents.add(new TimestampedGpsEvent(new GpsEvent("Tracker1", 10.0, 20.0, 1000.0)));
        gpsEvents.add(new TimestampedGpsEvent(new GpsEvent("Tracker1", 11.0, 21.0, 2000.0)));
        GpsGUI.trackerDataMap.put("Tracker1", gpsEvents);

        DefaultTableModel rightTableModel = Mockito.mock(DefaultTableModel.class);
        GpsGUI.rightTableModel = rightTableModel;

        // Update total distance
        GpsGUI.updateTotalDistance("Tracker1", 0);

        // Verify the right table model has been updated with the correct distance
        Mockito.verify(rightTableModel).setValueAt(Mockito.eq(156240), Mockito.eq(0), Mockito.eq(3));
    }

    @Test
    public void testUpdateLeftTable() {
        // Mock left table model
        DefaultTableModel leftTableModel = Mockito.mock(DefaultTableModel.class);
        GpsGUI.leftTableModel = leftTableModel;

        // Update left table with tracker index 1, latitude 15.0, and longitude 30.0
        GpsGUI.updateLeftTable(1, 15.0, 30.0);

        // Verify the table values have been updated correctly
        Mockito.verify(leftTableModel).setValueAt(15.0, 1, 1);
        Mockito.verify(leftTableModel).setValueAt(30.0, 1, 2);
    }
}
