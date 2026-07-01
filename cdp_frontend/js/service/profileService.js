import { getProfiles } from "../api/profileApi";
import { mapProfile } from "../mapper/profileMapper";
export async function loadProfiles() {

    const page = await getProfiles();

    return page.content.map(mapProfile);

}