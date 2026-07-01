async function getProfiles() {

    const response = await fetch("http://localhost:9001/v1/admin/profiles");

    if (!response.ok) {
        throw new Error("Cannot load profiles");
    }

    const json = await response.json();

    return json.data.content.map(profile => ({
        id: profile.id,
        name: profile.fullName,
        cdpId: profile.profileCode,
        avatarText: profile.avatarText,
        email: profile.email,
        phone: profile.phone,
        customerType: profile.customerTypeText || "Chưa xác định",
        duplicateStatus: profile.warningStatus === "CONFLICT"
            ? "suspect"
            : "normal",
        duplicateStatusText: profile.warningText,
        sources: profile.sourceSystems,
        lastActivity: profile.lastActivityAt
    }));
}