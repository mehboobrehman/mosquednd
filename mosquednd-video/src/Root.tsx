import React from "react";
import { Composition } from "remotion";
import { MainComposition } from "./MainComposition";
import "./index.css";

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition
        id="MosqueDNDTutorial"
        component={MainComposition}
        durationInFrames={1140}
        fps={30}
        width={1920}
        height={1080}
      />
    </>
  );
};
