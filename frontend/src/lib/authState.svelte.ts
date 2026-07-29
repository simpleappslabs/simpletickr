let username = $state<string | null>(null);
let checked = $state(false);

export const authState = {
	get username() {
		return username;
	},
	set username(value: string | null) {
		username = value;
	},
	get checked() {
		return checked;
	},
	set checked(value: boolean) {
		checked = value;
	},
};
