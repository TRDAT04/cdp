export function mapProfile(profile) {

    return {

        id: profile.id,

        name: profile.fullName,

        cdpId: profile.profileCode,

        avatarText: profile.avatarText,

        email: profile.email,

        phone: profile.phone,

        customerType:
            profile.customerTypeText || "Chưa xác định",

        status: profile.statusText,

       duplicateStatus:
    profile.warningStatus === "CONFLICT"
        ? "suspect"
        : "normal",

        duplicateStatusText:
            profile.warningText,

        sources:
            profile.sourceSystems,

        lastActivity:
            profile.lastActivityAt

    };

}