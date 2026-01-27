package com.groom.order.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserAddressResponse {

	private String recipientName;
	private String recipientPhone;
	private String zipCode;
	private String address;
	private String detailAddress;
}
