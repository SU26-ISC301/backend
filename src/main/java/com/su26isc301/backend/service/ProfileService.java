package com.su26isc301.backend.service;

import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final ProfileRepository profileRepository;
    public Profile getProfileById(UUID userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với ID: " + userId));
    }


    public Profile createProfile(Profile profile) {
        if (profileRepository.findByEmail(profile.getEmail()).isPresent()) {
            throw new RuntimeException("Email này đã tồn tại");
        }
        if (profileRepository.findByPhone(profile.getPhone()).isPresent()) {
            throw new RuntimeException("Số điện thoại này đã tồn tại");
        }
        return profileRepository.save(profile);
    }

    public List<Profile> getAllProfiles() {
        return profileRepository.findAll();
    }
}
