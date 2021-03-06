//
//  FDevice.h
//  Roger
//
//  Created by Bill Phillips on 5/30/12.
//  Copyright (c) 2012 Big Nerd Ranch. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface FDevice : NSObject

@property (nonatomic, copy) NSString *serial;
@property (assign) BOOL hasWifiConnection;
@property (assign) BOOL hasAdbConnection;

@end
