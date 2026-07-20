package com.manuelorg.cross_pesa.kycSubmission.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    /**
     * Uploads an image to Cloudinary and returns the secure HTTPS URL.
     *
     * @param file The image file received from the frontend
     * @param folder The folder name in Cloudinary (e.g., "kyc_documents")
     * @return The secure URL of the uploaded image
     */
    @SuppressWarnings("unchecked")
    public String uploadImage(MultipartFile file, String folder) throws IOException {
        // We set the folder parameter so your Cloudinary dashboard stays organized
        Map<String, Object> uploadParams = ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "auto" // Automatically handles images, pdfs, etc.
        );

        Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadParams);

        // Return the secure URL provided by Cloudinary
        return uploadResult.get("secure_url").toString();
    }

    /**
     * Optional: Delete an image from Cloudinary (useful if KYC is rejected and you want to purge PII)
     */
    public void deleteImage(String publicId) throws IOException {
        cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
    }
    /**
     * Uploads an image to Cloudinary directly from an external URL (like Smile ID's servers).
     */
    @SuppressWarnings("unchecked")
    public String uploadImageFromUrl(String imageUrl, String folder) throws IOException {
        Map<String, Object> uploadParams = ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "auto"
        );

        Map<?, ?> uploadResult = cloudinary.uploader().upload(imageUrl, uploadParams);
        return uploadResult.get("secure_url").toString();
    }
}
