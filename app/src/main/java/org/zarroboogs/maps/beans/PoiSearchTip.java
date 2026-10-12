package org.zarroboogs.maps.beans;

public class PoiSearchTip {

    private String name;
    private String district;
    private String adcode;

    public PoiSearchTip() {
    }

    public PoiSearchTip(String name, String district, String adcode) {
        this.name = name;
        this.district = district;
        this.adcode = adcode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getAdcode() {
        return adcode;
    }

    public void setAdcode(String adcode) {
        this.adcode = adcode;
    }
}
