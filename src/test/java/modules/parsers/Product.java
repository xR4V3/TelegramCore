package modules.parsers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Product {
    private String id;
    private String name;
    private BigDecimal price;
    private BigDecimal specialPrice;
    private String description;
    private Map<String, String> characteristics = new HashMap<>();

    // остатки
    private Integer totalStock;  // общий (rest)
    private Integer proStock;    // по PRO-складам (rest1)

    private String unit;
    private String url;       // B2B ссылка
    private String imageUrl;  // main_photo

    // дополнительные поля для "красивого" XML
    private String brand;
    private String country;
    private String weight;              // строкой
    private String characteristicsText; // уже собранный многострочный текст
    private List<String> photos;        // все фото
    private List<String> alsoBuy;       // артикулы alsobuy
    private List<String> allImages = new ArrayList<>();

    private String categoryGroupName; // CategoryGroupName из JSON
    private String categoryName;      // CategoryName
    private String subcategoryName;   // SubcategoryName

    public String getCategoryGroupName() {
        return categoryGroupName;
    }

    public void setCategoryGroupName(String categoryGroupName) {
        this.categoryGroupName = categoryGroupName;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getSubcategoryName() {
        return subcategoryName;
    }

    public void setSubcategoryName(String subcategoryName) {
        this.subcategoryName = subcategoryName;
    }

    public List<String> getAllImages() { return allImages; }
    public void setAllImages(List<String> allImages) { this.allImages = allImages; }


    public Product() {}

    public Product(String id) {
        this.id = id;
    }

    // ===== Геттеры/сеттеры =====
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getSpecialPrice() { return specialPrice; }
    public void setSpecialPrice(BigDecimal specialPrice) { this.specialPrice = specialPrice; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, String> getCharacteristics() { return characteristics; }
    public void setCharacteristics(Map<String, String> characteristics) { this.characteristics = characteristics; }

    public Integer getTotalStock() { return totalStock; }
    public void setTotalStock(Integer totalStock) { this.totalStock = totalStock; }

    public Integer getProStock() { return proStock; }
    public void setProStock(Integer proStock) { this.proStock = proStock; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }

    public String getCharacteristicsText() { return characteristicsText; }
    public void setCharacteristicsText(String characteristicsText) { this.characteristicsText = characteristicsText; }

    public List<String> getPhotos() { return photos; }
    public void setPhotos(List<String> photos) { this.photos = photos; }

    public List<String> getAlsoBuy() { return alsoBuy; }
    public void setAlsoBuy(List<String> alsoBuy) { this.alsoBuy = alsoBuy; }

    @Override
    public String toString() {
        return String.format(
                "Product{id='%s', name='%s', price=%s, specialPrice=%s, totalStock=%d, proStock=%d, unit='%s'}",
                id, name, price, specialPrice, totalStock, proStock, unit
        );
    }
}
