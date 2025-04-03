import java.awt.*;
import java.util.*;
import java.util.Timer;
import nz.sodium.*;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * This class is used to associate a GpsEvent with a timestamp 
 * This class serves as a wrapper around the GpsEvent, adding a timestamp field 
 */
class TimestampedGpsEvent {
    GpsEvent event;
    long time;

    /**
     * Constructs a new TimestampedGpsEvent by wrapping the provided GpsEvent
     * The timestamp is set to the current system time in milliseconds
     *
     * @param event The GpsEvent to be wrapped with a timestamp
     */
    public TimestampedGpsEvent(GpsEvent event) {
        this.event = event;
        this.time = System.currentTimeMillis();
    }
}

/**
 * This class is responsible for creating and managing the graphical user interface (GUI) that displays GPS tracker data  
 * This class uses Sodium FRP for handling real-time updates and interactions
 */
public class GpsGUI {
    /** 
     * The model for the table displayed on the left panel of the GUI
     * showing basic tracker information including Tracker No., Latitude, and Longitude
     */
    public static DefaultTableModel leftTableModel;

    /**
     * The model for the table displayed on the right panel of the GUI
     * showing filtered tracker information along with calculated distance
     */
    public static DefaultTableModel rightTableModel;

    /**
     * Display the current event information in the GUI
     */
    public static JLabel eventLabel;

    /**
     * Reset the eventLabel text after 3 seconds
     */
    public static Timer resetEventTimer;

    /**
     * A JLabel that shows the current latitude and longitude values as set by the user 
     * through the filter input fields in the GUI. It provides visual feedback on the applied filter criteria.
     */
    public static JLabel currentLatLonLabel;

    /**
     * A deque of TimestampedGpsEvent objects for each tracker
     * stores the GPS events with their associated timestamps
     */
    public static Map<String, Deque<TimestampedGpsEvent>> trackerDataMap = new HashMap<>();

    /**
     * Total distance covered by each tracker in meters
     * updated whenever new GPS events are processed
     */
    public static Map<String, Double> trackerDistanceMap = new HashMap<>();

    /**
     * Updates the left table model with the latest latitude and longitude values for the specified tracker
     * 
     * @param trackerIndex The index of the tracker to update in the table
     * @param latitude The latest latitude value of the tracker to display
     * @param longitude The latest longitude value of the tracker to display
     */
    public static void updateLeftTable(int trackerIndex, double latitude, double longitude) {
        leftTableModel.setValueAt(latitude, trackerIndex, 1);
        leftTableModel.setValueAt(longitude, trackerIndex, 2);
    }

    /**
     * Updates the right table model with the latest latitude, longitude, and the total distance covered by the tracker within the last 5 minutes
     * 
     * @param trackerIndex The index of the tracker to update in the right table
     * @param latitude The latest latitude value of the tracker to display
     * @param longitude The latest longitude value of the tracker to display
     * @param altitude The latest altitude value of the tracker to display
     * @param trackerName The name of the tracker
     */
    public static void updateRightTable(int trackerIndex, double latitude, double longitude, double altitude, String trackerName) {
        // 1. Updates the "Latitude" and "Longitude" columns in the right table model at the specified row
        rightTableModel.setValueAt(latitude, trackerIndex, 1); 
        rightTableModel.setValueAt(longitude, trackerIndex, 2);

        // 2. Creates a new `GpsEvent` and wraps it with a `TimestampedGpsEvent` that includes the current system time
        GpsEvent newEvent = new GpsEvent(trackerName, latitude, longitude, altitude);
        TimestampedGpsEvent timestampedEvent = new TimestampedGpsEvent(newEvent);

        // 3. Adds the new event to a deque that stores the last 5 minutes of GPS events for the tracker
        Deque<TimestampedGpsEvent> gpsEvents = trackerDataMap.getOrDefault(trackerName, new LinkedList<>());
        gpsEvents.addLast(timestampedEvent);

        // 4. Removes events that are older than five minutes from the deque
        long currentTimeMillis = System.currentTimeMillis();
        while (!gpsEvents.isEmpty() && currentTimeMillis - gpsEvents.peekFirst().time > 300000) {
            gpsEvents.removeFirst();
        }

        // 5. Stores the updated deque back in the `trackerDataMap`
        trackerDataMap.put(trackerName, gpsEvents);

        // 6. Calls `updateTotalDistance` to recalculate the total distance covered by the tracker within the last five minutes based on the filtered GPS events in the deque
        updateTotalDistance(trackerName, trackerIndex);
    }

    /**
     * Resets the event label to "No Events" after a delay of 3 seconds 
     * whenever a new event is received. It cancels any existing timer and creates a new timer
     */
    private static void resetEventTimer() {
        // 1. Checks if the `resetEventTimer` is already running
        if (resetEventTimer != null) {
            resetEventTimer.cancel();
        }

        // 2. Initializes a new `Timer` object and assigns it to the `resetEventTimer` variable
        resetEventTimer = new Timer();

        // 3. Schedules a new `TimerTask` that will run after a delay of 3 seconds
        resetEventTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> eventLabel.setText("No Events"));
            }
        }, 3000);
    }

    /**
     * Allows only integer values to be entered into a text field
     *  
     * @return A `PlainDocument` instance that restricts input to integer values only
     */
    private static PlainDocument createIntegerOnlyDocument() {
        PlainDocument doc = new PlainDocument();

        // Set a custom DocumentFilter to allow only integer values to be entered
        doc.setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                if (string == null) return;
                if (isInteger(fb.getDocument().getText(0, fb.getDocument().getLength()) + string)) {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                if (text == null) return;
                String newValue = fb.getDocument().getText(0, offset) + text + fb.getDocument().getText(offset + length, fb.getDocument().getLength() - offset - length);
                if (isInteger(newValue)) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }

            @Override
            public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
                String newValue = fb.getDocument().getText(0, offset) + fb.getDocument().getText(offset + length, fb.getDocument().getLength() - offset - length);
                if (isInteger(newValue)) {
                    super.remove(fb, offset, length);
                }
            }

            /**
             * Checks if the given text is a valid integer
             * Allows empty strings and single '-' characters
             * 
             * @param text The text to check for validity as an integer
             * @return true if the text is a valid integer or a temporary valid state, false otherwise
             */
            private boolean isInteger(String text) {
                if (text == null || text.isEmpty()) return true;
                if (text.equals("-")) return true;
                try {
                    Integer.parseInt(text);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        });
        return doc;
    }

    /**
     * Updates a `CellSink<Double>` whenever the text in a given text field changes
     * Establishes a connection between a GUI text field and a `CellSink<Double>` from the Sodium 
     * Whenever the user types, deletes, or replaces text in the specified text field, the listener will parse the current text as a `Double` and send it to the `CellSink`
     *
     * @param cellSink The `CellSink<Double>` to which the parsed double values from the text field will be sent
     * @param textField The `JTextField` whose changes will be monitored by the `DocumentListener`
     * @return A `DocumentListener` instance that updates the `CellSink` with the latest value from the text field
     */
    private static DocumentListener createDocumentListener(CellSink<Double> cellSink, JTextField textField) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateCell();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateCell();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateCell();
            }
            
            /**
             * Helper method to update the `CellSink` with the latest value from the text field
             * 
             * Retrieves the current text from the text field, parses it as a `Double`, and sends it to the `CellSink`
             * If the value cannot be parsed as a `Double`, `null` will be sent to the `CellSink` instead
             */
            private void updateCell() {
                Double value = getDoubleFromTextField(textField);
                cellSink.send(value);
            }
        };
    }
    
    /**
     * Retrieves the current text value from the specified `JTextField` and parses it as a `Double`
     * 
     * @param textField The `JTextField` whose text value needs to be parsed as a `Double`
     * @return The parsed `Double` value if the text is a valid representation of a double otherwise `null`
     */
    private static Double getDoubleFromTextField(JTextField textField) {
        try {
            Double value = Double.parseDouble(textField.getText());
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Calculates and updates the total distance traveled by a specified tracker within the last 5 minutes
     * 
     * @param trackerName The name of the tracker for which the total distance is being calculated (e.g., "Tracker1")
     * @param trackerIndex The index of the tracker in the right table, corresponding to the row number where the distance will be displayed
     */
    public static void updateTotalDistance(String trackerName, int trackerIndex) {
        // 1. Retrieves the deque of `TimestampedGpsEvent` objects for the specified tracker using the tracker name
        Deque<TimestampedGpsEvent> gpsEvents = trackerDataMap.get(trackerName);

        // 2. Checks if the deque is `null` or contains fewer than two events
        if (gpsEvents == null || gpsEvents.size() < 2) {
            rightTableModel.setValueAt(0, trackerIndex, 3);
            return;
        }

        // 3. Iterates through the deque and calculates the distance between consecutive GPS events
        double totalDistance = 0;
        TimestampedGpsEvent prevEvent = null;
        for (TimestampedGpsEvent event : gpsEvents) {
            if (prevEvent != null) {
                totalDistance += calculateDistance(prevEvent.event, event.event);
            }
            prevEvent = event;
        }

        // 4. Updates the `trackerDistanceMap` with the newly calculated total distance for the tracker
        int roundedDistance = (int) Math.ceil(totalDistance);
        trackerDistanceMap.put(trackerName, (double) roundedDistance);

        // 5. Updates the right table model to display the calculated distance in the appropriate row
        rightTableModel.setValueAt(roundedDistance, trackerIndex, 3);
    }

    /**
     * Calculates the straight-line distance between two GPS events
     * It considers the latitude, longitude, and altitude differences to determine the total distance traveled between the two points. 
     * Altitude is given in feet in the `GpsEvent` objects, so it is converted to meters before calculating the distance.
     * 
     * @param prevEvent The previous GPS event (starting point).
     * @param currentEvent The current GPS event (ending point).
     * @return The straight-line distance between the two GPS events in meters.
     */
    public static double calculateDistance(GpsEvent prevEvent, GpsEvent currentEvent) {
        // 1. Converts the altitude values of both GPS events from feet to meters using the conversion factor `0.3048`
        final double METERS_PER_FOOT = 0.3048;
        
        // 2. Computes the differences in latitude (`latDiff`), longitude (`lonDiff`), and altitude (`altitudeDiff`)
        double latDiff = currentEvent.latitude - prevEvent.latitude;
        double lonDiff = currentEvent.longitude - prevEvent.longitude;

        double metersPerDegreeLatitude = 111320;
        double metersPerDegreeLongitude = 111320 * Math.cos(Math.toRadians(prevEvent.latitude));
        
        double prevAltitude = prevEvent.altitude * METERS_PER_FOOT;
        double currentAltitude = currentEvent.altitude * METERS_PER_FOOT;
        double altitudeDiff = currentAltitude - prevAltitude;

        // 3. Calculates the horizontal distance between the two points using the Pythagorean theorem: `sqrt(latDiff^2 + lonDiff^2)`
        double latDistance = latDiff * metersPerDegreeLatitude;
        double lonDistance = lonDiff * metersPerDegreeLongitude;
        double horizontalDistance = Math.sqrt(latDistance * latDistance + lonDistance * lonDistance);
        
        // 4. Computes the total distance by combining the horizontal distance with the altitude difference
        double totalDistance = Math.sqrt(horizontalDistance * horizontalDistance + altitudeDiff * altitudeDiff);
        return totalDistance;
    }

    public static void main(String[] args) {

        // ======= Main frame =======
        JFrame frame = new JFrame("GPS");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 800);

        // Create a horizontal split pane to separate the GUI into left and right panels
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerSize(0);
        splitPane.setResizeWeight(0.5);

        // ======= Left panel =======
        JPanel leftDisplay = new JPanel();
        leftDisplay.setLayout(new BorderLayout());
        leftDisplay.setBackground(Color.LIGHT_GRAY);
        leftDisplay.setBorder(BorderFactory.createTitledBorder("Tracker"));

        // 1.1 Left upper panel: 10 tracker simplified display
        String[] columnNamesLeft = {"Tracker No.", "Latitude", "Longitude"};
        leftTableModel = new DefaultTableModel(new Object[10][3], columnNamesLeft);
        for (int i = 0; i < 10; i++) {
            leftTableModel.setValueAt(i, i, 0);
        }

        JTable tableLeft = new JTable(leftTableModel);
        tableLeft.setPreferredScrollableViewportSize(new Dimension(500, 350));
        tableLeft.setFillsViewportHeight(true);
        tableLeft.setEnabled(false);
        tableLeft.setRowHeight(25);

        JScrollPane scrollPaneLeft = new JScrollPane(tableLeft);
        leftDisplay.add(scrollPaneLeft, BorderLayout.CENTER);

        // 1.2 Left lower panel: event display
        JPanel lowerPanel = new JPanel();
        lowerPanel.setPreferredSize(new Dimension(500, 250));
        lowerPanel.setBackground(Color.WHITE);
        lowerPanel.setBorder(BorderFactory.createTitledBorder("Current Event"));
        lowerPanel.setLayout(new BorderLayout());

        eventLabel = new JLabel("No Event");
        eventLabel.setHorizontalAlignment(SwingConstants.CENTER);
        eventLabel.setFont(new Font("Serif", Font.PLAIN, 13));
        lowerPanel.add(eventLabel, BorderLayout.CENTER);

        leftDisplay.add(lowerPanel, BorderLayout.SOUTH);

        // ======= Right panel =======
        JPanel rightDisplay = new JPanel();
        rightDisplay.setLayout(new BorderLayout());
        rightDisplay.setBackground(Color.LIGHT_GRAY);
        rightDisplay.setBorder(BorderFactory.createTitledBorder("Filtered Tracker"));

        // 2.1 Right upper panel: 10 tracker simplified display & distance
        String[] columnNamesRight = {"Tracker No.", "Latitude", "Longitude", "Distance"};
        rightTableModel = new DefaultTableModel(new Object[10][4], columnNamesRight);
        for (int i = 0; i < 10; i++) {
            rightTableModel.setValueAt(i, i, 0);
        }

        JTable tableRight = new JTable(rightTableModel);
        tableRight.setPreferredScrollableViewportSize(new Dimension(500, 350));
        tableRight.setFillsViewportHeight(true);
        tableRight.setEnabled(false);
        tableRight.setRowHeight(25);

        JScrollPane scrollPaneRight = new JScrollPane(tableRight);
        rightDisplay.add(scrollPaneRight, BorderLayout.NORTH);

        // 2.2 Right lower panel
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new GridLayout(4, 2, 5, 5)); 
        inputPanel.setBorder(BorderFactory.createTitledBorder("Filter"));
        inputPanel.setPreferredSize(new Dimension(500, 250));

        // 2.2.1 Latitude
        JLabel latLabelMin = new JLabel("Latitude Min(-90~90)");
        JTextField latMinField = new JTextField(5);
        latMinField.setPreferredSize(new Dimension(50, 20));
        latMinField.setDocument(createIntegerOnlyDocument());

        JLabel latLabelMax = new JLabel("Latitude Max(-90~90)");
        JTextField latMaxField = new JTextField(5);
        latMaxField.setPreferredSize(new Dimension(50, 20));
        latMaxField.setDocument(createIntegerOnlyDocument());

        // 2.2.2 Longitude
        JLabel lonLabelMin = new JLabel("Longitude Min(-180~180)");
        JTextField lonMinField = new JTextField(5);
        lonMinField.setPreferredSize(new Dimension(50, 20));
        lonMinField.setDocument(createIntegerOnlyDocument());

        JLabel lonLabelMax = new JLabel("Longitude Max(-180~180)");
        JTextField lonMaxField = new JTextField(5);
        lonMaxField.setPreferredSize(new Dimension(50, 20));
        lonMaxField.setDocument(createIntegerOnlyDocument());

        // Add labels and text fields to the input panel
        inputPanel.add(latLabelMin);
        inputPanel.add(latMinField);
        inputPanel.add(latLabelMax);
        inputPanel.add(latMaxField);
        inputPanel.add(lonLabelMin);
        inputPanel.add(lonMinField);
        inputPanel.add(lonLabelMax);
        inputPanel.add(lonMaxField);
        
        // 2.2.3 Ristriction
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(2, 1, 5, 5)); 
        buttonPanel.setPreferredSize(new Dimension(500, 80));

        currentLatLonLabel = new JLabel("Current Latitude: not set | Current Longitude: not set");
        currentLatLonLabel.setHorizontalAlignment(SwingConstants.CENTER);
        buttonPanel.add(currentLatLonLabel);

        // 2.2.4 Set
        JButton setButton = new JButton("Set");
        setButton.setPreferredSize(new Dimension(100, 30));

        JPanel setButtonPanel = new JPanel();
        buttonPanel.add(setButtonPanel);
        setButtonPanel.add(setButton);

        // Add input panel and button panel to the right panel
        rightDisplay.add(inputPanel, BorderLayout.CENTER);
        rightDisplay.add(buttonPanel, BorderLayout.SOUTH);

        // Add left and right panels to the split pane
        splitPane.setLeftComponent(leftDisplay);
        splitPane.setRightComponent(rightDisplay);

        // Add the split pane to the frame and set frame properties
        frame.add(splitPane);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // ======= Sodium FRP =======

        // Create CellSinks for the min and max latitude and longitude
        CellSink<Double> minLatCell = new CellSink<>(null);
        CellSink<Double> maxLatCell = new CellSink<>(null);
        CellSink<Double> minLonCell = new CellSink<>(null);
        CellSink<Double> maxLonCell = new CellSink<>(null);

        // Create a StreamSink for the Set button to trigger an action when the button is pressed
        StreamSink<Unit> setButtonStream = new StreamSink<>();

        // Add document listeners to the latitude and longitude input fields
        latMinField.getDocument().addDocumentListener(createDocumentListener(minLatCell, latMinField));
        latMaxField.getDocument().addDocumentListener(createDocumentListener(maxLatCell, latMaxField));
        lonMinField.getDocument().addDocumentListener(createDocumentListener(minLonCell, lonMinField));
        lonMaxField.getDocument().addDocumentListener(createDocumentListener(maxLonCell, lonMaxField));

        // Define a snapshot of the CellSink values when the Set button is clicked
        // It uses the snapshot function to create a formatted string that combines the minimum and maximum latitudes and longitudes
        // The formatting checks that the entered values are valid lat/lon ranges
        setButtonStream.snapshot(
            minLatCell.lift(maxLatCell, (minLat, maxLat) -> {
                if (minLat == null || maxLat == null || minLat < -90 || minLat > 90 || maxLat < -90 || maxLat > 90) {
                    return null;
                }
                String minLatFormatted = String.valueOf(Math.round(minLat));
                String maxLatFormatted = String.valueOf(Math.round(maxLat));
                return Double.parseDouble(minLatFormatted) <= Double.parseDouble(maxLatFormatted)
                    ? minLatFormatted + " ~ " + maxLatFormatted
                    : null;
            })
        ).snapshot(
            minLonCell.lift(maxLonCell, (minLon, maxLon) -> {
                if (minLon == null || maxLon == null || minLon < -180 || minLon > 180 || maxLon < -180 || maxLon > 180) {
                    return null;
                }
                String minLonFormatted = String.valueOf(Math.round(minLon));
                String maxLonFormatted = String.valueOf(Math.round(maxLon));
                return Double.parseDouble(minLonFormatted) <= Double.parseDouble(maxLonFormatted)
                    ? minLonFormatted + " ~ " + maxLonFormatted
                    : null;
            }),
            (latRange, lonRange) -> {
                String latText = (latRange != null) ? latRange : "not set";
                String lonText = (lonRange != null) ? lonRange : "not set";
                return "Current Latitude: " + latText + " | Current Longitude: " + lonText;
            }
        ).filter(labelText -> labelText != null)
        .listen(labelText -> {
            SwingUtilities.invokeLater(() -> currentLatLonLabel.setText(labelText));
        });

        // Set button action listener to reset the right table values and trigger the update by sending the event into the StreamSink
        setButton.addActionListener(e -> {
            // 1. Clear the right table values for latitude, longitude, and distance for all trackers
            for (int i = 0; i < 10; i++) {
                for (int j = 1; j < 4; j++) {
                    rightTableModel.setValueAt(null, i, j);
                }
            }
        
            // 2. Clear all data in trackerDistanceMap and trackerDataMap
            trackerDistanceMap.clear();
            trackerDataMap.clear();
        
            // 3. Trigger the set button stream to indicate that the filter has been applied and data should be reprocessed
            setButtonStream.send(Unit.UNIT);
        });

        // ======= GPS Event Handling =======
        // Initialize the GPS service and get the event streams for each tracker
        GpsService serv = new GpsService();
        Stream<GpsEvent>[] streams = serv.getEventStreams();

        // Map each tracker name (e.g., "Tracker0", "Tracker1") to its corresponding table row index
        Map<String, Integer> trackerNameToIndex = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            trackerNameToIndex.put("Tracker" + i, i);
        }

        // Create a combined stream to aggregate events from all GPS stream
        StreamSink<GpsEvent> combinedStream = new StreamSink<>();

        // Use Sodium's Transaction mechanism to process GPS events
        Transaction.runVoid(() -> {
            for (Stream<GpsEvent> stream : streams) {
                stream.listen(ev -> {
                    Transaction.post(() -> combinedStream.send(ev));
                });
            }

            // Create a CellLoop to hold the current GPS event being processed
            CellLoop<GpsEvent> currentEventCell = new CellLoop<>();
            Cell<GpsEvent> currentEvent = combinedStream.hold(null);
            currentEventCell.loop(currentEvent);

            // Listen for changes in the current GPS event and update the event label
            currentEvent.listen(ev -> {
                if (ev != null) {
                    resetEventTimer();
                    eventLabel.setText(ev.name + ", " + ev.latitude + ", " + ev.longitude + ", " + ev.altitude);
                } else {
                    eventLabel.setText("No Events");
                }
            });

            // Listen to the individual GPS event streams and update the tables accordingly
            for (Stream<GpsEvent> stream : streams) {
                stream.listen((GpsEvent ev) -> {
                    Integer trackerIndex = trackerNameToIndex.get(ev.name);
                    if (trackerIndex != null) {
                        updateLeftTable(trackerIndex, ev.latitude, ev.longitude);

                        Double minLat = minLatCell.sample();
                        Double maxLat = maxLatCell.sample();
                        Double minLon = minLonCell.sample();
                        Double maxLon = maxLonCell.sample();

                        boolean inLatRange = (minLat == null || ev.latitude >= minLat) && (maxLat == null || ev.latitude <= maxLat);
                        boolean inLonRange = (minLon == null || ev.longitude >= minLon) && (maxLon == null || ev.longitude <= maxLon);


                        if (inLatRange && inLonRange) {
                            updateRightTable(trackerIndex, ev.latitude, ev.longitude, ev.altitude, ev.name);
                        }
                    }
                });
            }
        });
    }
}
