export interface SeedUser {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  defaultRole: string;
  imageUrl: string;
}

const defaultUsers: SeedUser[] = [
  {
    id: "user_e2e_alice",
    firstName: "Alice",
    lastName: "Owner",
    email: "alice@e2e-test.local",
    defaultRole: "owner",
    imageUrl: "https://api.dicebear.com/7.x/initials/svg?seed=AO",
  },
  {
    id: "user_e2e_bob",
    firstName: "Bob",
    lastName: "Admin",
    email: "bob@e2e-test.local",
    defaultRole: "admin",
    imageUrl: "https://api.dicebear.com/7.x/initials/svg?seed=BA",
  },
  {
    id: "user_e2e_carol",
    firstName: "Carol",
    lastName: "Member",
    email: "carol@e2e-test.local",
    defaultRole: "member",
    imageUrl: "https://api.dicebear.com/7.x/initials/svg?seed=CM",
  },
];

function loadUsers(): Record<string, SeedUser> {
  const users: Record<string, SeedUser> = {};

  for (const user of defaultUsers) {
    users[user.id] = user;
  }

  const envUsers = process.env.MOCK_USERS;
  if (envUsers) {
    try {
      const extraUsers: SeedUser[] = JSON.parse(envUsers);
      for (const user of extraUsers) {
        users[user.id] = user;
      }
    } catch (err) {
      console.error("Failed to parse MOCK_USERS env var:", err);
    }
  }

  return users;
}

export const USERS = loadUsers();
