package br.com.rerono.codec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class Base64Handler {
    
    private static final Logger logger = LoggerFactory.getLogger(Base64Handler.class);
    
    public static byte[] decode(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            logger.warn("Tentativa de decodificar string Base64 nula ou vazia");
            return new byte[0];
        }
        
        try {
            String normalized = normalize(base64String);
            
            if (normalized.isEmpty()) {
                logger.warn("String Base64 ficou vazia após normalização");
                return new byte[0];
            }
            
            byte[] decoded = Base64.getDecoder().decode(normalized);
            
            logger.debug("Base64 decodificado: {} caracteres -> {} bytes", 
                normalized.length(), decoded.length);
            
            return decoded;
            
        } catch (IllegalArgumentException e) {
            logger.error("Erro ao decodificar Base64: {}", e.getMessage());
            throw new IllegalArgumentException("String Base64 inválida: " + e.getMessage(), e);
        }
    }
    
    public static String encode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(data);
    }
    
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        
        String normalized = input.replaceAll("\\s+", "");
        
        if (normalized.startsWith("\uFEFF")) {
            normalized = normalized.substring(1);
        }
        
        return normalized.trim();
    }
    
    public static String calculateSha256(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            logger.error("Algoritmo SHA-256 não disponível", e);
            throw new RuntimeException("SHA-256 não suportado", e);
        }
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    public static boolean isValidBase64(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        
        try {
            String normalized = normalize(input);
            Base64.getDecoder().decode(normalized);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    public static boolean isPdf(byte[] data) {
        if (data == null || data.length < 5) {
            return false;
        }
        return data[0] == 0x25 && data[1] == 0x50 && 
               data[2] == 0x44 && data[3] == 0x46 && data[4] == 0x2D;
    }
    
    public static boolean isPng(byte[] data) {
        if (data == null || data.length < 8) {
            return false;
        }
        return data[0] == (byte) 0x89 && data[1] == 0x50 && 
               data[2] == 0x4E && data[3] == 0x47 &&
               data[4] == 0x0D && data[5] == 0x0A && 
               data[6] == 0x1A && data[7] == 0x0A;
    }
    
    public static boolean isJpeg(byte[] data) {
        if (data == null || data.length < 3) {
            return false;
        }
        return data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF;
    }
    
    public static String detectFileType(byte[] data) {
        if (isPdf(data)) return "PDF";
        if (isPng(data)) return "PNG";
        if (isJpeg(data)) return "JPG";
        return "BIN";
    }
}