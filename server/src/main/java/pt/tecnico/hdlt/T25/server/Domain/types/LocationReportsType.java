package pt.tecnico.hdlt.T25.server.Domain.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import pt.tecnico.hdlt.T25.server.Domain.LocationReport;

import java.util.ArrayList;
import java.util.List;

public class LocationReportsType {
    @JsonProperty("locationReportsList")
    private final List<CustomLocationReport> locationReportsList;

    @JsonCreator
    public LocationReportsType() {
        this.locationReportsList = new ArrayList<>();
    }

    public void updateLocationReports(int userId, int epoch, LocationReport locationReport) {
        this.locationReportsList.add(new CustomLocationReport(userId, epoch, locationReport));
    }

    public LocationReport getLocationReport(int userId, int epoch) {
        for (CustomLocationReport customLocationReport : locationReportsList)
            if (customLocationReport.getUserId() == userId && customLocationReport.getEpoch() == epoch)
                return customLocationReport.getLocationReport();
        return null;
    }

    private static class CustomLocationReport {
        @JsonProperty("userId")
        private final int userId;
        @JsonProperty("epoch")
        private final int epoch;
        @JsonProperty("locationReport")
        private final LocationReport locationReport;

        @JsonCreator
        public CustomLocationReport(@JsonProperty("userId") int userId, @JsonProperty("epoch") int epoch, @JsonProperty("locationReport") LocationReport locationReport) {
            this.userId = userId;
            this.epoch = epoch;
            this.locationReport = locationReport;
        }

        public int getUserId() {
            return userId;
        }

        public int getEpoch() {
            return epoch;
        }

        public LocationReport getLocationReport() {
            return locationReport;
        }
    }
}
