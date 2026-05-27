package com.nc.stock.project.stock.dto;

public class ForeignMarginDto {
    private String id;
    private String name;
    private int age;

    // 기본 생성자, Getter, Setter 필요 (Jackson 역직렬화용)
    public ForeignMarginDto() {}
    
    public ForeignMarginDto(String id, String name, int age) {
        this.id = id;
        this.name = name;
        this.age = age;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
}
