package hu.kisspd.citydp.gui;

import hu.kisspd.citydp.MySQLConn;
import hu.kisspd.citydp.Util;
import hu.kisspd.citydp.model.City;
import hu.kisspd.citydp.model.Line;

import javax.swing.*;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.QuadCurve2D;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JMapPanel extends JPanel {
    private final HashMap<Integer, City> cities = new HashMap<>();
    private final HashMap<Integer, Line> lines = new HashMap<>();

    private List<City> aStarPath = null;

    public JMapPanel() {
        super();

        loadCities();
        loadLines();
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        // keep 2:1 ratio
        int size = Math.min(getWidth(), getHeight());
        return new Dimension(size, size / 2);
    }

    private void loadCities() {
        try (ResultSet rs = MySQLConn.runQuery("SELECT * FROM settlement")) {
            while (rs != null && rs.next()) {
                City city = City.fromResultSet(rs);
                cities.put(city.getId(), city);
            }
        } catch (Exception e) {
            Util.showError("Hiba történt a városok betöltése közben:", e);
        }
    }

    private void loadLines() {
        try (ResultSet rs = MySQLConn.runQuery("SELECT * FROM route")) {
            Map<Integer, City> citiesCopy = getCities();
            while (rs != null && rs.next()) {
                Line line = Line.fromResultSet(rs, citiesCopy);
                lines.put(line.getId(), line);
            }
        } catch (Exception e) {
            Util.showError("Hiba történt a vonalak betöltése közben:", e);
        }
    }

    public void reloadMap() {
        cities.clear();
        lines.clear();
        loadCities();
        loadLines();
        revalidate();
        repaint();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g;

        drawLines(g2d);
        drawCities(g);
        drawAStarPath(g2d);
    }

    private void drawLines(Graphics2D g2d) {
        ArrayList<Double[]> controlPoints = new ArrayList<>();
        for (Line line : lines.values()) {
            City cityFrom = line.getCityFrom();
            int x1 = (int) (cityFrom.getLocX() * this.getWidth());
            int y1 = (int) (cityFrom.getLocY() * this.getHeight());

            City cityTo = line.getCityTo();
            int x2 = (int) (cityTo.getLocX() * this.getWidth());
            int y2 = (int) (cityTo.getLocY() * this.getHeight());

            g2d.setStroke(new BasicStroke(4));
            g2d.setColor(line.getColor());

            Double[] controlPoint = createControlPoint(x1, y1, x2, y2, controlPoints);
            controlPoints.add(controlPoint);
            double controlX = controlPoint[0], controlY = controlPoint[1];

            g2d.draw(new QuadCurve2D.Double(x1, y1, controlX, controlY, x2, y2));

            drawLineArrow(new int[]{x1, y1}, new int[]{(int) controlX, (int) controlY},
                    new int[]{x2, y2}, g2d);
        }
    }

    private void drawCities(Graphics g) {
        g.setColor(Color.BLACK);
        for (City city : cities.values()) {
            int radius = city.getType().getRadius();
            int x = (int) (city.getLocX() * this.getWidth()) - radius;
            int y = (int) (city.getLocY() * this.getHeight()) - radius;
            int size = city.getType().getSize();

            g.fillOval(x, y, size, size);
        }
    }

    private void drawAStarPath(Graphics2D g2d) {
        if (aStarPath != null) {
            for (int i = 0; i < aStarPath.size() - 1; i++) {
                Stroke dashed = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                        0, new float[]{9}, 0);

                g2d.setStroke(dashed);
                g2d.setColor(Color.RED);

                City cityFrom = aStarPath.get(i);
                int x1 = (int) (cityFrom.getLocX() * this.getWidth());
                int y1 = (int) (cityFrom.getLocY() * this.getHeight());

                City cityTo = aStarPath.get(i + 1);
                int x2 = (int) (cityTo.getLocX() * this.getWidth());
                int y2 = (int) (cityTo.getLocY() * this.getHeight());

                g2d.drawLine(x1, y1, x2, y2);
            }
            aStarPath = null;
        }
    }

    private Double[] createControlPoint(double x1, double y1, double x2, double y2,
                                        ArrayList<Double[]> controlPoints) {
        double rotate = Math.PI / 2.0 * 3;
        double angle = (Math.atan2(y2 - y1, x2 - x1) + rotate) % (Math.PI * 2);
        double length = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        double shift = length / 5.0;

        double controlX = (x1 + x2) / 2.0 + Math.cos(angle) * shift;
        double controlY = (y1 + y2) / 2.0 + Math.sin(angle) * shift;
        while (Util.containsArray(controlPoints, new Double[]{controlX, controlY})) {
            shift *= 1.2;
            controlX = (x1 + x2) / 2.0 + Math.cos(angle) * shift;
            controlY = (y1 + y2) / 2.0 + Math.sin(angle) * shift;
        }

        return new Double[]{controlX, controlY};
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void drawLineArrow(int[] p1, int[] controlPoint, int[] p2, Graphics2D g2d) {
        int[] arrowPos = Util.quadLerpPoint(p1, controlPoint, p2, 0.5f);

        Font font = new Font("Arial", Font.BOLD, 20);

        Map attributes = font.getAttributes();
        attributes.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
        Font fontWithLigatures = font.deriveFont(attributes);

        int x1 = p1[0], y1 = p1[1], x2 = p2[0], y2 = p2[1];
        double angle = Math.atan2(y2 - y1, x2 - x1);
        AffineTransform transform = new AffineTransform();
        transform.rotate(angle, 0, 0);

        Font rotatedFont = fontWithLigatures.deriveFont(transform);
        g2d.setFont(rotatedFont);
        g2d.setColor(Color.BLACK);

        int[] centeredPos = Util.centerText(arrowPos[0], arrowPos[1], "-->",
                g2d.getFontMetrics(fontWithLigatures), transform);
        g2d.drawString("-->", centeredPos[0], centeredPos[1]);
    }

    public City searchNearbyCity(double x, double y) {
        double searchGap = 3.0 / this.getWidth();

        for (City city : cities.values()) {
            double cityX = city.getLocX(), cityY = city.getLocY();
            double distX = Math.abs(cityX - x), distY = Math.abs(cityY - y);
            double citySize = (double) city.getType().getSize() / this.getWidth();

            if (distX <= citySize + searchGap && distY <= citySize + searchGap) {
                return city;
            }
        }

        return null;
    }

    public Map<Integer, City> getCities() {
        return Map.copyOf(cities);
    }

    public Map<Integer, Line> getLines() {
        return Map.copyOf(lines);
    }

    public void clearCities() {
        lines.clear();
        cities.clear();
        this.revalidate();
        this.repaint();
    }

    public void addCity(City city) {
        cities.put(city.getId(), city);
        this.revalidate();
        this.repaint();
    }

    public void addLine(Line line) {
        lines.put(line.getId(), line);
        this.revalidate();
        this.repaint();
    }

    public void removeCity(City city) {
        cities.remove(city.getId());
        this.revalidate();
        this.repaint();
    }

    public void removeLine(Line line) {
        lines.remove(line.getId());
        this.revalidate();
        this.repaint();
    }

    public int getCityCount() {
        return cities.size();
    }

    public void setAStarPath(List<City> aStarPath) {
        this.aStarPath = new ArrayList<>(aStarPath);
        this.revalidate();
        this.repaint();
    }
}
