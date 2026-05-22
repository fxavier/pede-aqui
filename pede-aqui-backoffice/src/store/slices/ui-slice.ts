import { createSlice, PayloadAction } from "@reduxjs/toolkit";

type UiState = {
  sidebarCollapsed: boolean;
  activeGroup: string | null;
  searchQuery: string;
};

const initialState: UiState = {
  sidebarCollapsed: false,
  activeGroup: null,
  searchQuery: "",
};

const uiSlice = createSlice({
  name: "ui",
  initialState,
  reducers: {
    toggleSidebar(state) {
      state.sidebarCollapsed = !state.sidebarCollapsed;
    },
    setActiveGroup(state, action: PayloadAction<string | null>) {
      state.activeGroup = action.payload;
    },
    setSearchQuery(state, action: PayloadAction<string>) {
      state.searchQuery = action.payload;
    },
  },
});

export const { toggleSidebar, setActiveGroup, setSearchQuery } = uiSlice.actions;
export default uiSlice.reducer;
