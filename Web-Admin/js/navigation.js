// js/navigation.js

const DASHBOARDS = {

    principal: "principal-dashboard.html",

    sub_admin: "sub-admin-dashboard.html",

    election_commission: "ec-dashboard.html",

    presiding_officer: "presiding-dashboard.html",

    assistant_presiding_officer: "presiding-dashboard.html"

};


export function getDashboardForRole(role) {

    return DASHBOARDS[role] || "login.html";

}


export function goToDashboard(role) {

    window.location.href =
        getDashboardForRole(role);

}