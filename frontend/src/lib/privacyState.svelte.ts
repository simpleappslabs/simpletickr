let masked = $state(false);

export const privacyState = {
	get masked() {
		return masked;
	},
	set masked(value: boolean) {
		masked = value;
	},
};
