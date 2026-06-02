package com.delivery.catalog.service;

import java.util.List;
import java.util.Map;

/** Helper class for managing vertical-specific product attributes. */
public class ProductAttributeHelper {
    
    /** Restaurant/Food vertical attributes */
    public static class Restaurant {
        public static final String INGREDIENTS = "ingredients";
        public static final String ALLERGENS = "allergens";
        public static final String NUTRITIONAL_INFO = "nutritional";
        public static final String PREPARATION_TIME = "prepTime";
        public static final String DIETARY_RESTRICTIONS = "dietary";
        public static final String SERVING_SIZE = "servingSize";
        public static final String SPICE_LEVEL = "spiceLevel";
        
        @SuppressWarnings("unchecked")
        public static List<String> getIngredients(Map<String, Object> attributes) {
            return (List<String>) attributes.get(INGREDIENTS);
        }
        
        @SuppressWarnings("unchecked")
        public static List<String> getAllergens(Map<String, Object> attributes) {
            return (List<String>) attributes.get(ALLERGENS);
        }
        
        public static Integer getPreparationTime(Map<String, Object> attributes) {
            return (Integer) attributes.get(PREPARATION_TIME);
        }
    }
    
    /** Grocery/Retail vertical attributes */
    public static class Grocery {
        public static final String BRAND = "brand";
        public static final String EXPIRY_DATE = "expiryDate";
        public static final String NUTRITIONAL_INFO = "nutritional";
        public static final String ORGANIC = "organic";
        public static final String WEIGHT = "weight";
        public static final String DIMENSIONS = "dimensions";
        public static final String STORAGE_INSTRUCTIONS = "storage";
        
        public static String getBrand(Map<String, Object> attributes) {
            return (String) attributes.get(BRAND);
        }
        
        public static Boolean isOrganic(Map<String, Object> attributes) {
            return (Boolean) attributes.get(ORGANIC);
        }
        
        public static String getWeight(Map<String, Object> attributes) {
            return (String) attributes.get(WEIGHT);
        }
    }
    
    /** Electronics vertical attributes */
    public static class Electronics {
        public static final String BRAND = "brand";
        public static final String MODEL = "model";
        public static final String TECHNICAL_SPECS = "techSpecs";
        public static final String WARRANTY_INFO = "warranty";
        public static final String COMPATIBILITY = "compatibility";
        public static final String POWER_REQUIREMENTS = "power";
        public static final String DIMENSIONS = "dimensions";
        
        public static String getBrand(Map<String, Object> attributes) {
            return (String) attributes.get(BRAND);
        }
        
        public static String getModel(Map<String, Object> attributes) {
            return (String) attributes.get(MODEL);
        }
        
        @SuppressWarnings("unchecked")
        public static Map<String, String> getTechnicalSpecs(Map<String, Object> attributes) {
            return (Map<String, String>) attributes.get(TECHNICAL_SPECS);
        }
    }
    
    /** Pharmacy vertical attributes */
    public static class Pharmacy {
        public static final String DRUG_NAME = "drugName";
        public static final String DOSAGE = "dosage";
        public static final String ACTIVE_INGREDIENTS = "activeIngredients";
        public static final String CONTRAINDICATIONS = "contraindications";
        public static final String SIDE_EFFECTS = "sideEffects";
        public static final String PRESCRIPTION_REQUIRED = "prescriptionRequired";
        public static final String BATCH_NUMBER = "batchNumber";
        
        public static String getDrugName(Map<String, Object> attributes) {
            return (String) attributes.get(DRUG_NAME);
        }
        
        public static String getDosage(Map<String, Object> attributes) {
            return (String) attributes.get(DOSAGE);
        }
        
        public static Boolean isPrescriptionRequired(Map<String, Object> attributes) {
            return (Boolean) attributes.get(PRESCRIPTION_REQUIRED);
        }
    }
    
    /** Florist vertical attributes */
    public static class Florist {
        public static final String FLOWER_TYPE = "flowerType";
        public static final String COLOR_SCHEME = "colorScheme";
        public static final String OCCASION = "occasion";
        public static final String SEASONAL_AVAILABILITY = "seasonal";
        public static final String CARE_INSTRUCTIONS = "careInstructions";
        public static final String ARRANGEMENT_SIZE = "arrangementSize";
        
        public static String getFlowerType(Map<String, Object> attributes) {
            return (String) attributes.get(FLOWER_TYPE);
        }
        
        @SuppressWarnings("unchecked")
        public static List<String> getOccasions(Map<String, Object> attributes) {
            return (List<String>) attributes.get(OCCASION);
        }
        
        public static String getArrangementSize(Map<String, Object> attributes) {
            return (String) attributes.get(ARRANGEMENT_SIZE);
        }
    }
    
    /** Helper method to validate attributes for a given vertical */
    public static boolean isValidVerticalAttribute(String vertical, String attributeKey) {
        return switch (vertical.toLowerCase()) {
            case "restaurants" -> isRestaurantAttribute(attributeKey);
            case "grocery" -> isGroceryAttribute(attributeKey);
            case "electronics" -> isElectronicsAttribute(attributeKey);
            case "pharmacy" -> isPharmacyAttribute(attributeKey);
            case "florists" -> isFloristAttribute(attributeKey);
            default -> true; // Allow any attribute for other verticals
        };
    }
    
    private static boolean isRestaurantAttribute(String key) {
        return key.equals(Restaurant.INGREDIENTS) || key.equals(Restaurant.ALLERGENS) ||
               key.equals(Restaurant.NUTRITIONAL_INFO) || key.equals(Restaurant.PREPARATION_TIME) ||
               key.equals(Restaurant.DIETARY_RESTRICTIONS) || key.equals(Restaurant.SERVING_SIZE) ||
               key.equals(Restaurant.SPICE_LEVEL);
    }
    
    private static boolean isGroceryAttribute(String key) {
        return key.equals(Grocery.BRAND) || key.equals(Grocery.EXPIRY_DATE) ||
               key.equals(Grocery.NUTRITIONAL_INFO) || key.equals(Grocery.ORGANIC) ||
               key.equals(Grocery.WEIGHT) || key.equals(Grocery.DIMENSIONS) ||
               key.equals(Grocery.STORAGE_INSTRUCTIONS);
    }
    
    private static boolean isElectronicsAttribute(String key) {
        return key.equals(Electronics.BRAND) || key.equals(Electronics.MODEL) ||
               key.equals(Electronics.TECHNICAL_SPECS) || key.equals(Electronics.WARRANTY_INFO) ||
               key.equals(Electronics.COMPATIBILITY) || key.equals(Electronics.POWER_REQUIREMENTS) ||
               key.equals(Electronics.DIMENSIONS);
    }
    
    private static boolean isPharmacyAttribute(String key) {
        return key.equals(Pharmacy.DRUG_NAME) || key.equals(Pharmacy.DOSAGE) ||
               key.equals(Pharmacy.ACTIVE_INGREDIENTS) || key.equals(Pharmacy.CONTRAINDICATIONS) ||
               key.equals(Pharmacy.SIDE_EFFECTS) || key.equals(Pharmacy.PRESCRIPTION_REQUIRED) ||
               key.equals(Pharmacy.BATCH_NUMBER);
    }
    
    private static boolean isFloristAttribute(String key) {
        return key.equals(Florist.FLOWER_TYPE) || key.equals(Florist.COLOR_SCHEME) ||
               key.equals(Florist.OCCASION) || key.equals(Florist.SEASONAL_AVAILABILITY) ||
               key.equals(Florist.CARE_INSTRUCTIONS) || key.equals(Florist.ARRANGEMENT_SIZE);
    }
}