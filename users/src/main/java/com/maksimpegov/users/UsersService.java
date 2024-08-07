package com.maksimpegov.users;

import com.maksimpegov.users.exeption.ApiRequestException;
import com.maksimpegov.users.models.PasswordEditRequest;
import com.maksimpegov.users.models.UserServiceResponse;
import com.maksimpegov.users.models.TokenDto;
import com.maksimpegov.users.user.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class UsersService {
	@Value("${spring.constraints.security.url}")
	private String securityMicroserviceUrl;

	@Value("${spring.constraints.userMicroserviceIdentifier}")
	private String USER_MICROSERVICE_IDENTIFIER;

	private final RestTemplate restTemplate;
	private final UsersRepository usersRepository;

	private final UserMapper mapper;

	@Autowired
	public UsersService(RestTemplate restTemplate, UsersRepository usersRepository, UserMapper mapper) {
		this.restTemplate = restTemplate;
		this.usersRepository = usersRepository;
		this.mapper = mapper;
	}

	public void registerUser(UserDto userDto) {
		try {
			User user = mapper.userDtoToUser(userDto);

			if (user.getUsername() == null || user.getPassword() == null) {
				throw new ApiRequestException("Invalid request", "Username or password is empty", 400);
			} else if (!user.userValidation()) {
				throw new ApiRequestException("Invalid request", "Username or password is not valid", 400);

			} else if (usersRepository.findByUsername(user.getUsername()) != null) {
				throw new ApiRequestException("Conflict", "User with this username already exists", 409);
			}

			// Id generation
			long currentMillis = System.currentTimeMillis();
			byte[] bytes = String.valueOf(currentMillis).getBytes();  // Convert current time to byte array
			UUID timeBasedUUID = UUID.nameUUIDFromBytes(bytes);
			String userId = timeBasedUUID.toString();

			user.setId(userId);
			user.encryptPassword();
			user.setCreated_at(new Date());
			usersRepository.save(user);
		} catch (ApiRequestException e) {
			throw e;
		} catch (Exception e) {
			throw new ApiRequestException("Internal server error", e.getMessage(), 500);
		}
	}

	public TokenDto loginUser(UserDto userDto) {
		try {
			User user = mapper.userDtoToUser(userDto);

			if (user.getUsername() == null || user.getPassword() == null) {
				throw new ApiRequestException("Invalid request", "Username or password is empty", 400);
			}

			if (!doesUserExist(user.getUsername())) {
				throw new ApiRequestException("Not found", "User with this username does not exist", 404);
			}

			if (!passwordVerification(user.getUsername(), user.getPassword())) {
				throw new ApiRequestException("Unauthorized", "Wrong password", 401);
			}

			User userFromDb = usersRepository.findByUsername(user.getUsername());
			
			// Setting up headers for security microservice
			HttpHeaders headers = new HttpHeaders();
			headers.set("Authorization", USER_MICROSERVICE_IDENTIFIER);
			HttpEntity<Object> httpEntity = new HttpEntity<>(headers);

			String url = securityMicroserviceUrl + "/" + userFromDb.getId();
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, httpEntity, String.class);

			String token = response.getBody();
      return new TokenDto(token);
			
		} catch (ApiRequestException e) {
			throw e;
		} catch (Exception e) {
			throw new ApiRequestException("Internal server error", e.getMessage(), 500);
		}
	}

	public UserInfo getUserInfo(String id) {
		try {
			Optional<User> userFromDb = usersRepository.findById(id);

			if (userFromDb.isEmpty()) {
				throw new ApiRequestException("Not found", "User with this id does not exist", 404);
			}
			User user = userFromDb.get();

			user.hidePassword();
			return mapper.userToUserInfo(user);
		} catch (ApiRequestException e) {
			throw e;
		} catch (Exception e) {
			throw new ApiRequestException("Internal server error", e.getMessage(), 500);
		}
	}

	public void editPassword(PasswordEditRequest editRequest) {
		try {
			if (editRequest.getUsername() == null || editRequest.getOldPassword() == null || editRequest.getNewPassword() == null) {
				throw new ApiRequestException("Invalid request", "Username or password is empty", 400);
			}

			if (!doesUserExist(editRequest.getUsername())) {
				throw new ApiRequestException("Not found", "User with this username does not exist", 404);
			}

			if (!passwordVerification(editRequest.getUsername(), editRequest.getOldPassword())) {
				throw new ApiRequestException("Unauthorized", "You provided wrong old password", 401);
			}

			User userFromDb = usersRepository.findByUsername(editRequest.getUsername());
			userFromDb.setPassword(editRequest.getNewPassword());
			userFromDb.encryptPassword();

			if (!userFromDb.userValidation()) {
				throw new ApiRequestException("Invalid request", "New password is not valid", 400);
			}
			usersRepository.save(userFromDb); // everything is ok, save new password
		} catch (ApiRequestException e) {
			throw e;
		} catch (Exception e) {
			throw new ApiRequestException("Internal server error", e.getMessage(), 500);
		}
	}

	public void deleteUser(String userId) {
		try {
			Optional<User> optionalUser = usersRepository.findById(userId);
			if (optionalUser.isEmpty()) {
				throw new ApiRequestException("Not found", "User with this id does not exist", 404);
			}
			User user = optionalUser.get();

			// TODO: Request collection microservice to delete all collections for current user(collection microservice required)

			if (true) { // if collection was deleted successfully 
				usersRepository.delete(user);
				new UserServiceResponse(204, "User deleted successfully");
			}
		} catch (ApiRequestException e) {
			throw e;
		} catch (Exception e) {
			throw new ApiRequestException("Internal server error", e.getMessage(), 500);
		}
	}

	private boolean doesUserExist(String username) {
		return usersRepository.findByUsername(username) != null;
	}

	private boolean passwordVerification(String username, String password) {
		BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
		String truePassword = usersRepository.findByUsername(username).getPassword();
		return passwordEncoder.matches(password, truePassword);
	}
}
