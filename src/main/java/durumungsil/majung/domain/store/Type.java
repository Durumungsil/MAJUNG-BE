package durumungsil.majung.domain.store;

public enum Type {
    RETAIL("G2", "소매"),
    LODGING("I1", "숙박"),
    FOOD("I2", "음식"),
    REAL_ESTATE("L1", "부동산"),
    SCIENCE_TECHNOLOGY("M1", "과학·기술"),
    FACILITY_RENTAL("N1", "시설관리·임대"),
    EDUCATION("P1", "교육"),
    HEALTHCARE("Q1", "보건의료"),
    ART_SPORTS("R1", "예술·스포츠"),
    REPAIR_PERSONAL("S2", "수리·개인");

    private final String code;
    private final String displayName;

    Type(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Type fromCode(String code) {
        for (Type type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 상권업종대분류코드: " + code);
    }
}
